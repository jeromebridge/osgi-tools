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
}
