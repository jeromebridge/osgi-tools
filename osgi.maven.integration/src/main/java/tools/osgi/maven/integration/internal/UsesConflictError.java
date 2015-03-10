package tools.osgi.maven.integration.internal;

public class UsesConflictError {
   private String bundleSymbolicName;
   private String importPackageName;
   private String importPackageVersion;

   public UsesConflictError( String importPackageName, String importPackageVersion, String bundleSymbolicName ) {
      setImportPackageName( importPackageName );
      setImportPackageVersion( importPackageVersion );
      setBundleSymbolicName( bundleSymbolicName );
   }

   public String getBundleSymbolicName() {
      return bundleSymbolicName;
   }

   public String getImportPackageName() {
      return importPackageName;
   }

   public String getImportPackageVersion() {
      return importPackageVersion;
   }

   public void setBundleSymbolicName( String bundleSymbolicName ) {
      this.bundleSymbolicName = bundleSymbolicName;
   }

   public void setImportPackageName( String importPackageName ) {
      this.importPackageName = importPackageName;
   }

   public void setImportPackageVersion( String importPackageVersion ) {
      this.importPackageVersion = importPackageVersion;
   }

   @Override
   public String toString() {
      return String.format( "Uses Conflict: %s %s (%s)", importPackageName, importPackageVersion, bundleSymbolicName );
   }
}
