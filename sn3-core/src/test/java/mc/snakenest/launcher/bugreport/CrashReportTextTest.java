package mc.snakenest.launcher.bugreport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrashReportTextTest {
    @Test void hidesTheSnakeSeedInArgumentsOrWithoutAFieldName(){String seed="ab".repeat(32);String text=CrashReportText.sanitize("args: -Dsn3.token="+seed+"\nraw: "+seed);assertFalse(text.contains(seed));assertTrue(text.contains("[masqué]"));}
    @Test void hidesMinecraftTokensAndAuthenticationHeaders(){String text=CrashReportText.sanitize("--accessToken very-private-value Authorization: Bearer abc.def.ghi");assertFalse(text.contains("very-private-value"));assertFalse(text.contains("abc.def.ghi"));}
    @Test void hidesJsonAndSignedQueryFields(){String text=CrashReportText.sanitize("{\"seed\":\"private-seed\"} /api?signature=private-signature&other=visible");assertFalse(text.contains("private-seed"));assertFalse(text.contains("private-signature"));assertTrue(text.contains("other=visible"));}
    @Test void keepsTheCrashUseful(){String text="java.lang.OutOfMemoryError: Java heap space\n at example.Mod.run(Mod.java:42)\nMinecraft 1.21.11 - exit code 1";assertEquals(text,CrashReportText.sanitize(text));}
    @Test void repeatedFilteringPreservesTheReport(){String once=CrashReportText.sanitize("seed=private --accessToken secret {\"token\":\"secret\"} Authorization: Bearer abc");assertEquals(once,CrashReportText.sanitize(once));}
    @Test void supportsMissingLogs(){assertEquals("",CrashReportText.sanitize(null));assertEquals("",CrashReportText.sanitize(""));}
    @Test void doesNotLeakMixedCaseKeys(){String text=CrashReportText.sanitize("SN3.TOKEN=secret-value password=secret-password refresh_token=secret-refresh");assertFalse(text.contains("secret-value"));assertFalse(text.contains("secret-password"));assertFalse(text.contains("secret-refresh"));}
    @Test void hidesBasicAuthorization(){assertFalse(CrashReportText.sanitize("Authorization: Basic dXNlcjpwYXNz").contains("dXNlcjpwYXNz"));}
    @Test void preservesContextAndLastErrorsWithinTheServerLimit(){String text="CONTEXT\n"+"x".repeat(100000)+"\nLAST ERROR";String result=CrashReportText.forSubmission(text);assertTrue(result.length()<=65000);assertTrue(result.startsWith("CONTEXT"));assertTrue(result.endsWith("LAST ERROR"));assertTrue(result.contains("abrégé"));}
}

