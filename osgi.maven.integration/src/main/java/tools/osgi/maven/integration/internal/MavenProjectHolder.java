package tools.osgi.maven.integration.internal;

import hudson.maven.MavenEmbedder;

import org.apache.maven.project.MavenProject;

public class MavenProjectHolder {
   private MavenEmbedder embedder;
   private MavenProject project;

   public MavenProjectHolder( MavenEmbedder embedder, MavenProject project ) {
      setEmbedder( embedder );
      setProject( project );
   }

   public MavenEmbedder getEmbedder() {
      return embedder;
   }

   public void setEmbedder( MavenEmbedder embedder ) {
      this.embedder = embedder;
   }

   public MavenProject getProject() {
      return project;
   }

   public void setProject( MavenProject project ) {
      this.project = project;
   }

   @Override
   public int hashCode() {
      int hash = 17;
      hash = 31 * hash + project.hashCode();
      return hash;
   }

   @Override
   public boolean equals( Object other ) {
      return project != null && other != null && other instanceof MavenProjectHolder && project.equals( ( ( MavenProjectHolder )other ).getProject() );
   }
}
