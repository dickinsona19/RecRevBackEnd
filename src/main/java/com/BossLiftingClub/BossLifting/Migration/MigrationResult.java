package com.BossLiftingClub.BossLifting.Migration;

import java.util.ArrayList;
import java.util.List;

public class MigrationResult {
    private int totalProcessed;
    private int imported;
    private int skipped;
    private int errors;
    private List<String> skipReasons = new ArrayList<>();
    private List<String> errorMessages = new ArrayList<>();
    private List<String> importedMembers = new ArrayList<>();
    private boolean dryRun;

    public int getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }
    public int getImported() { return imported; }
    public void setImported(int imported) { this.imported = imported; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getErrors() { return errors; }
    public void setErrors(int errors) { this.errors = errors; }
    public List<String> getSkipReasons() { return skipReasons; }
    public void setSkipReasons(List<String> skipReasons) { this.skipReasons = skipReasons; }
    public List<String> getErrorMessages() { return errorMessages; }
    public void setErrorMessages(List<String> errorMessages) { this.errorMessages = errorMessages; }
    public List<String> getImportedMembers() { return importedMembers; }
    public void setImportedMembers(List<String> importedMembers) { this.importedMembers = importedMembers; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
}
