package tools.osgi.analyzer.api;

/** Enumerates all the possible suggestions to fix a Use Conflict */
public enum UsesConflictResolutionSuggestionType {
   Null(""),
   None("None"),
   UninstallBundle("Uninstall Bundle"),
   RefreshBundle("Refresh Bundle"), ;

   private String display;

   private UsesConflictResolutionSuggestionType( String display ) {
      this.display = display;
   }

   public String display() {
      return display;
   }

   @Override
   public String toString() {
      return display();
   }
}
