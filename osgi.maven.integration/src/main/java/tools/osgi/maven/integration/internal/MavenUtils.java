package tools.osgi.maven.integration.internal;

import hudson.maven.MavenEmbedder;
import hudson.maven.MavenRequest;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import tools.osgi.maven.integration.internal.aether.Booter;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

public class MavenUtils {

   public static RepositorySystemSession getRepositorySystemSession( ClassLoader classLoader ) {
      try {
         final MavenRequest mavenRequest = new MavenRequest();
         final MavenEmbedder mavenEmbedder = new MavenEmbedder( classLoader, mavenRequest );
         final RepositorySystem system = Booter.newRepositorySystem();
         final RepositorySystemSession session = Booter.newRepositorySystemSession( system, new LocalRepository( mavenEmbedder.getLocalRepositoryPath() ) );
         return session;
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Failed creating Repository System Session", exception );
      }
   }

   public static BundleManifest getBundleManifest( Artifact dependency ) {
      try {
         BundleManifest result = null;
         final JarFile jar = new JarFile( dependency.getFile() );
         final ZipEntry manifestEntry = jar.getEntry( JarFile.MANIFEST_NAME );
         if( manifestEntry != null ) {
            final Reader reader = new InputStreamReader( jar.getInputStream( manifestEntry ) );
            result = BundleManifestFactory.createBundleManifest( reader, new DummyParserLogger() );
            jar.close();
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting bundle manifest for dependency: %s", dependency ), exception );
      }
   }

   public static BundleManifest getBundleManifest( MavenProject project ) {
      try {
         BundleManifest result = null;
         final File manifestFile = getManifestFile( project );
         if( manifestFile.exists() ) {
            final Reader reader = new FileReader( manifestFile );
            result = BundleManifestFactory.createBundleManifest( reader, new DummyParserLogger() );
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed getting manifest for Maven Project: %s", project.getArtifactId() ) );
      }
   }

   public static File getManifestFile( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() + File.separator + "META-INF/MANIFEST.MF" );
   }

   public static List<Artifact> resolveDependencies( MavenProjectHolder holder, RepositorySystemSession sessionOverride ) {
      final List<Artifact> result = new ArrayList<Artifact>();
      final RepositorySystem system = Booter.newRepositorySystem();
      final RepositorySystemSession session = sessionOverride != null ? sessionOverride : Booter.newRepositorySystemSession( system, new LocalRepository( holder.getEmbedder().getLocalRepositoryPath() ) );
      for( Dependency dependency : holder.getProject().getDependencies() ) {
         try {
            org.eclipse.aether.artifact.Artifact filter = new org.eclipse.aether.artifact.DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion() );
            final DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter( JavaScopes.RUNTIME );
            final CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot( new org.eclipse.aether.graph.Dependency( filter, JavaScopes.RUNTIME ) );
            collectRequest.setRepositories( Booter.newRepositories( system, session ) );
            for( ArtifactRepository remoteRepository : holder.getProject().getRemoteArtifactRepositories() ) {
               collectRequest.addRepository( new RemoteRepository.Builder( remoteRepository.getId(), "default", remoteRepository.getUrl() ).build() );
            }
            final DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, classpathFlter );
            final List<ArtifactResult> artifactResults = system.resolveDependencies( session, dependencyRequest ).getArtifactResults();
            for( ArtifactResult artifactResult : artifactResults ) {
               result.add( artifactResult.getArtifact() );
            }
         }
         catch( Throwable exception ) {
            throw new RuntimeException( String.format( "Error resolving dependency: %s", dependency ), exception );
         }
      }
      return result;
   }

   public static List<Artifact> resolveDependencies( MavenProjectHolder holder ) {
      return resolveDependencies( holder, null );
   }
}
