package mc.snakenest.launcher.benchmark;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BenchmarkReportTest {
    @Test void screenshotValuesDoNotValidateHeavyPacksShadersOr120Fps() {
        String r=BenchmarkReport.describe(552,121.5,2.16,"Adreno 660",false,null);
        assertTrue(r.contains("552,0"));assertTrue(r.contains("121,5"));assertTrue(r.contains("2,16"));
        assertTrue(r.contains("moyens ou lourds : non validés"));assertTrue(r.contains("Shaders : non recommandés"));
        assertTrue(r.contains("Aucun de ces objectifs n’est validé"));
        assertFalse(r.contains("performances favorables"));assertFalse(r.contains("CPU favorable"));
    }
    @Test void evenLargeSyntheticScoresCannotValidateMinecraft() {
        String r=BenchmarkReport.describe(99999,99999,0.01,"GPU",false,null);
        assertTrue(r.contains("Minecraft ne sont pas validées"));assertTrue(r.contains("Shaders : non recommandés"));
    }
    @Test void invalidCpuDoesNotHideValidGpu() {
        String r=BenchmarkReport.describe(Double.NaN,121.5,2.16,"GPU",false,null);
        assertTrue(r.contains("Mesure partielle"));assertTrue(r.contains("121,5"));assertFalse(r.contains("NaN"));
    }
    @Test void softwareAndInvalidMeasurementsHaveNoHardwareScore() {
        String r=BenchmarkReport.describe(500,121.5,2.16,"llvmpipe",true,null);
        assertTrue(r.contains("Rendu logiciel"));assertFalse(r.contains("121,5"));
        r=BenchmarkReport.describe(Double.POSITIVE_INFINITY,Double.NaN,-1,null,false,"pilote indisponible");
        assertFalse(r.contains("NaN"));assertFalse(r.contains("Infinity"));assertTrue(r.contains("pilote indisponible"));
    }
}
