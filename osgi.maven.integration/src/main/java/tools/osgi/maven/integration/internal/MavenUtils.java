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
import org.apache.maven.model.Exclusion;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;

import tools.osgi.maven.integration.internal.aether.Booter;

import com.springsource.util.common.CaseInsensitiveMap;
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
      return resolveDependencies( holder, sessionOverride, new ArrayList<Artifact>() );
   }

   public static List<Artifact> resolveDependencies( MavenProjectHolder holder, RepositorySystemSession sessionOverride, List<Artifact> exclude ) {
      final List<Artifact> result = new ArrayList<Artifact>();
      final RepositorySystem system = Booter.newRepositorySystem();
      final RepositorySystemSession session = sessionOverride != null ? sessionOverride : Booter.newRepositorySystemSession( system, new LocalRepository( holder.getEmbedder().getLocalRepositoryPath() ) );
      for( Dependency dependency : holder.getProject().getDependencies() ) {
         org.eclipse.aether.artifact.Artifact dependencyArtifact = getAetherArtifact( dependency );
         boolean shouldResolveFlag = exclude == null || !exclude.contains( dependencyArtifact );
         if( shouldResolveFlag ) {
            try {
               final DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter( JavaScopes.RUNTIME );
               final DependencyFilter exclusionsFilter = getExclusionsFilter( dependency );
               final DependencyFilter filter = DependencyFilterUtils.andFilter( classpathFlter, exclusionsFilter );
               final CollectRequest collectRequest = new CollectRequest();
               collectRequest.setRoot( new org.eclipse.aether.graph.Dependency( dependencyArtifact, JavaScopes.RUNTIME ) );
               collectRequest.setRepositories( Booter.newRepositories( system, session ) );
               for( ArtifactRepository remoteRepository : holder.getProject().getRemoteArtifactRepositories() ) {
                  collectRequest.addRepository( new RemoteRepository.Builder( remoteRepository.getId(), "default", remoteRepository.getUrl() ).build() );
               }
               final DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, filter );
               final List<ArtifactResult> artifactResults = system.resolveDependencies( session, dependencyRequest ).getArtifactResults();
               for( ArtifactResult artifactResult : artifactResults ) {
                  result.add( artifactResult.getArtifact() );
               }
            }
            catch( Throwable exception ) {
               throw new RuntimeException( String.format( "Error resolving dependency: %s", dependency ), exception );
            }
         }
      }
      return result;
   }

   private static ExclusionsDependencyFilter getExclusionsFilter( Dependency dependency ) {
      final List<String> excludes = new ArrayList<String>();
      for( Exclusion exclusion : dependency.getExclusions() ) {
         excludes.add( String.format( "%s:%s", exclusion.getGroupId(), exclusion.getArtifactId() ) );
      }
      return new ExclusionsDependencyFilter( excludes );
   }

   public static org.eclipse.aether.artifact.Artifact getAetherArtifact( org.apache.maven.artifact.Artifact artifact ) {
      return new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), mapTypeToExtension( artifact.getType() ), artifact.getVersion() );
   }

   public static String mapTypeToExtension( String type ) {
      final CaseInsensitiveMap<String> map = new CaseInsensitiveMap<String>();
      map.put( "bundle", "jar" );
      return map.containsKey( type ) ? map.get( type ) : type;
   }

   public static org.eclipse.aether.artifact.Artifact getAetherArtifact( Dependency dependency ) {
      return new org.eclipse.aether.artifact.DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion() );
   }

   public static List<Artifact> resolveDependencies( MavenProjectHolder holder ) {
      return resolveDependencies( holder, new ArrayList<Artifact>() );
   }

   public static List<Artifact> resolveDependencies( MavenProjectHolder holder, List<Artifact> exclude ) {
      return resolveDependencies( holder, ( RepositorySystemSession )null, exclude );
   }
}
