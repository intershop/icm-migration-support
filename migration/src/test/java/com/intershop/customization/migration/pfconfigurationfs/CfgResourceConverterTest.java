package com.intershop.customization.migration.pfconfigurationfs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CfgResourceConverterTest
{
    /**
     * A managed service value containing '=' is the real case that stopped a migration: an encrypted credential whose
     * base64 payload carries '=' padding. Splitting on every '=' produced more than two parts, the line was skipped,
     * an empty key was stored instead, and building the entry then evaluated {@code "".substring(0, -1)}. The
     * exception aborted the whole run at step 050, so steps 055 through 901 never executed.
     */
    @Test
    void aValueContainingAnEqualsSignIsKeptWhole(@TempDir Path tempDir) throws IOException
    {
        String secret = "encryption0@PLAIN:djwkhNanjZQ=|QmVhcmVyIGV5SmhiR2Np";
        Path source = tempDir.resolve("development_mngdsrvc.resource");
        Files.write(source, List.of(
                "# a comment",
                "BOTISService_authorization.ServiceDefinitionID=BotisServiceDefinition",
                "BOTISService_authorization.ServiceConfigurationName=BOTIS_SERVICE_CHINA",
                "BOTISService_authorization.ParameterName=authorization",
                "BOTISService_authorization.Value=" + secret));
        Path target = tempDir.resolve("development_mngdsrvc.properties");

        CfgResourceConverter converter = new CfgResourceConverter("mngdsrvc", source, target);
        assertDoesNotThrow(converter::convertResource);

        List<String> result = Files.readAllLines(target);
        String entry = result.stream().filter(l -> l.contains("authorization =")).findFirst()
                .orElseThrow(() -> new AssertionError("no converted entry in " + result));

        assertEquals("pfconfigurationfs>mngdsrvc>BotisServiceDefinition>BOTIS_SERVICE_CHINA>authorization = " + secret,
                        entry);
    }

    /**
     * The payment service converter shares the code shape and had the same defect.
     */
    @Test
    void aPaymentServiceValueContainingAnEqualsSignIsKeptWhole(@TempDir Path tempDir) throws IOException
    {
        String secret = "abc==def";
        Path source = tempDir.resolve("development_pmntsrvc.resource");
        Files.write(source, List.of(
                "ConfigItem1.PaymentServiceID=MyPaymentService",
                "ConfigItem1.PaymentServiceConfigurationID=MyConfig",
                "ConfigItem1.ParameterName=secret",
                "ConfigItem1.Value=" + secret));
        Path target = tempDir.resolve("development_pmntsrvc.properties");

        new CfgResourceConverter("pmntsrvc", source, target).convertResource();

        List<String> result = Files.readAllLines(target);
        assertTrue(result.stream().anyMatch(l -> l.endsWith("secret = " + secret)), result.toString());
    }

    /**
     * A malformed line must be reported and skipped, never throw: an exception here aborts the migration.
     */
    @Test
    void malformedLinesAreSkippedRatherThanThrowing(@TempDir Path tempDir) throws IOException
    {
        Path source = tempDir.resolve("development_mngdsrvc.resource");
        Files.write(source, List.of(
                "a line with no separator at all",
                "KeyWithoutAGroupPrefix=value",
                ".leadingDot=value",
                "ConfigItem1.ServiceDefinitionID=Def",
                "ConfigItem1.ServiceConfigurationName=Name",
                "ConfigItem1.ParameterName=param",
                "ConfigItem1.Value=v"));
        Path target = tempDir.resolve("development_mngdsrvc.properties");

        CfgResourceConverter converter = new CfgResourceConverter("mngdsrvc", source, target);
        assertDoesNotThrow(converter::convertResource);

        // The well-formed entry still converts, so skipping bad lines does not lose the good ones.
        List<String> result = Files.readAllLines(target);
        assertTrue(result.stream().anyMatch(l -> l.equals("pfconfigurationfs>mngdsrvc>Def>Name>param = v")),
                        result.toString());
    }
}
