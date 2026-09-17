#!/usr/bin/env python3
"""Tier 0 smoke check for a migrated ICM server.

Answers one question: is the server wired correctly. It does NOT check that the data is right, and it
will happily pass a pipelet that resolves to a placeholder and returns an empty result. Smoke is
necessary, never sufficient. See the skill's `references/verifying-the-migration.md` for what covers
the rest.

**Make every check correspond to a defect this migration actually produced.** Then a regression in any
of them is a regression that already happened once, and the file doubles as a record of what went
wrong. Checks invented in the abstract tend to be the ones that never fail.

**Prefer asserting on data over asserting on a log line.** That a preparation step *created the service
configuration*, read back from the backoffice, is a stronger claim than that the step logged `Success`.

Run the negative controls once: point it at a wrong password and an unreachable host and confirm it
exits non-zero. A check that cannot fail is worth nothing.

Configuration comes from the environment, so pointing this at another server means changing ICM_BASE:

    export ICM_BASE=https://host:8443
    export ICM_BO_USER=admin
    export ICM_BO_PASSWORD=...          # never commit this
    export ICM_BO_ORG=<backoffice organization>
    python3 scripts/smoke.py

Exit code is 0 only if every check passes.
"""
import os
import sys

# icm_client.py ships with the icm-migration skill. Point this at wherever the plugin is installed,
# or vendor a copy next to this file.
sys.path.insert(0, os.environ.get(
    "ICM_CLIENT_DIR",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "icm_client")))
from icm_client import IcmClient, text_of                      # noqa: E402

BASE = os.environ.get("ICM_BASE", "https://localhost:8443")
USER = os.environ.get("ICM_BO_USER", "admin")
PASSWORD = os.environ.get("ICM_BO_PASSWORD", "")
ORG = os.environ.get("ICM_BO_ORG", "Operations")

# <project>: the organizations, channels and services this migration is supposed to have created.
EXPECTED_ORGANIZATIONS = ["<Organization>"]
EXPECTED_SERVICES = ["<Service Name>"]


def main():
    failures = []
    icm = IcmClient(BASE)

    def check(name, condition, detail=""):
        status = "ok" if condition else "FAIL"
        print(f"[{status}] {name}{(' : ' + detail) if detail and not condition else ''}")
        if not condition:
            failures.append(name)

    # 1. the server answers at all
    check("server responds", icm.get("/").status_code < 500)

    # 2. the backoffice is reachable and login works, which exercises component wiring
    logged_in = icm.backoffice_login(USER, PASSWORD, ORG)
    check("backoffice login", logged_in)

    if logged_in:
        # 3. the data the migration was supposed to create actually exists
        for organization in EXPECTED_ORGANIZATIONS:
            page = icm.organization_services(organization)
            body = text_of(page)
            check(f"organization {organization} exists", page.status_code == 200)
            for service in EXPECTED_SERVICES:
                check(f"{organization}: service {service}", service in body,
                      "not found in the service list")

    print()
    if failures:
        print(f"{len(failures)} check(s) failed: {', '.join(failures)}")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
