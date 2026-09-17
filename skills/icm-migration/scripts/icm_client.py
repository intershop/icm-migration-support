#!/usr/bin/env python3
"""Minimal HTTP client for talking to a running ICM server, including its backoffice.

Project-agnostic on purpose: it knows how ICM's login form works and nothing about any particular
project's organizations, services or URLs. Put those in a project-side script (see
`scripts/smoke.py` in the project this came from) and import this.

Why this exists: during a migration the fastest verification loop is not reading logs, it is asking
the running server. An agent container can usually reach the developer's ICM over HTTPS, so
"did the cartridge wire up" and "did the preparer actually create the data" can both be answered in
seconds. Checking that a dbinit step created a service configuration is much stronger evidence than
checking that the log line said Success.

Used as a library:

    from icm_client import IcmClient
    icm = IcmClient("https://host:8443")
    icm.get("/INTERSHOP/web/WFS/SMC/en_US/-/USD/SMCMain-Start")
    icm.login_backoffice("admin", password, "Operations")
    page = icm.organization_services("Acme-Global")

Used from the command line, for a quick look:

    python3 icm_client.py https://host:8443 /INTERSHOP/web/WFS/SLDSystem/

Certificate verification is off because development ICM instances use self-signed certificates.
Never point this at anything but a development or test server.
"""
import html as _html
import re
import ssl
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BACKOFFICE_PATH = "/INTERSHOP/web/WFS/SLDSystem/"


class IcmLoginError(RuntimeError):
    """Raised when the backoffice login did not land on a logged-in page."""


class IcmClient:
    def __init__(self, base, timeout=30):
        self.base = base.rstrip("/")
        self.timeout = timeout
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        self._opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=context),
            urllib.request.HTTPCookieProcessor(CookieJar()),
        )
        self._opener.addheaders = [("User-Agent", "icm-client/1.0")]

    # -- transport ---------------------------------------------------------

    def _open(self, url, data=None):
        full = url if url.startswith("http") else self.base + url
        body = urllib.parse.urlencode(data).encode() if data else None
        response = self._opener.open(full, body, timeout=self.timeout)
        return response, response.read().decode("utf-8", "replace")

    def get(self, url):
        """GET a URL or path. Returns (response, html)."""
        return self._open(url)

    def post(self, url, data):
        """POST a form. Returns (response, html)."""
        return self._open(url, data)

    # -- backoffice --------------------------------------------------------

    def login_backoffice(self, user, password, organization):
        """Log in to the SLDSystem backoffice. Returns the landing page HTML.

        ICM's login form posts to a ViewApplication-ProcessLogin URL carried in the form's action,
        so the action is read from the page rather than assumed; it embeds the host and locale.
        Staying on that URL afterwards means the login was rejected.
        """
        _, page = self.get(BACKOFFICE_PATH)
        action = re.search(r'<form action="([^"]+)"[^>]*name="LoginForm"', page)
        if not action:
            raise IcmLoginError("no LoginForm on %s%s" % (self.base, BACKOFFICE_PATH))
        response, page = self.post(action.group(1), {
            "LoginForm_Login": user,
            "LoginForm_Password": password,
            "LoginForm_RegistrationDomain": organization,
            "submit": "Log In",
        })
        if "ViewApplication-ProcessLogin" in response.url:
            raise IcmLoginError("login rejected for %s@%s" % (user, organization))
        self._landing = page
        return page

    def organization_services(self, organization, landing=None):
        """Return the HTML of an organization's Services view, or None if not reachable.

        Two hops, because both URLs carry a generated OrganizationUUID that cannot be guessed:
        the organization's edit page, then its definitions dispatch.
        """
        landing = landing or getattr(self, "_landing", None)
        if landing is None:
            raise IcmLoginError("log in before requesting organization services")
        link = re.search(
            r'href="([^"]*ViewOrganizationEnterprise-Edit[^"]*)"[^>]*>\s*' + re.escape(organization),
            landing)
        if not link:
            return None
        _, page = self.get(_html.unescape(link.group(1)))
        dispatch = re.search(r'href="([^"]*ViewOrganizationEnterpriseDefinitions-Dispatch[^"]*)"', page)
        if not dispatch:
            return None
        _, page = self.get(_html.unescape(dispatch.group(1)))
        return page


def text_of(page):
    """Strip tags and unescape entities, for substring checks against rendered content."""
    return _html.unescape(re.sub(r"<[^>]+>", " ", page))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    client = IcmClient(sys.argv[1])
    resp, body = client.get(sys.argv[2])
    print("HTTP %s  %s  %d bytes" % (resp.status, resp.url, len(body)))
    title = re.search(r"<title>(.*?)</title>", body, re.S)
    if title:
        print("title:", _html.unescape(title.group(1)).strip()[:150])
