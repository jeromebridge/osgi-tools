package tools.osgi.maven.integration.api;

import java.io.File;
import java.net.URI;

import org.eclipse.aether.artifact.Artifact;
import org.osgi.framework.Bundle;

public class DeployedMavenProject {
   private File mavenProjectFolder;
   private String checksum;
   private Artifact artifact;
   private Bundle bundle;
   private URI bundleUri;
   private File file;

   public File getFile() {
      return file;
   }

   public void setFile( File file ) {
      this.file = file;
   }

   public URI getBundleUri() {
      return bundleUri;
   }

   public void setBundleUri( URI bundleURI ) {
      this.bundleUri = bundleURI;
   }

   public Bundle getBundle() {
      return bundle;
   }

   public void setBundle( Bundle bundle ) {
      this.bundle = bundle;
   }

   public File getMavenProjectFolder() {
      return mavenProjectFolder;
   }

   public void setMavenProjectFolder( File mavenProjectFolder ) {
      this.mavenProjectFolder = mavenProjectFolder;
   }

   public String getChecksum() {
      return checksum;
   }

   public void setChecksum( String checksum ) {
      this.checksum = checksum;
   }

   public Artifact getArtifact() {
      return artifact;
   }

   public void setArtifact( Artifact artifact ) {
      this.artifact = artifact;
   }

   @Override
   public String toString() {
      return String.format( "Maven Project: %s(%s)", bundle.getSymbolicName(), bundle.getBundleId() );
   }
}
