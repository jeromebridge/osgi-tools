package tools.osgi.analyzer.api;

import org.osgi.framework.Bundle;

/** Represents a suggestion to fix a Use Conflict in the current OSGi container */
public class UsesConflictResolutionSuggestion {
   private UsesConflictResolutionSuggestionType type = UsesConflictResolutionSuggestionType.Null;
   private Bundle targetBundle;

   public void setType( UsesConflictResolutionSuggestionType type ) {
      this.type = type;
   }

   public Bundle getTargetBundle() {
      return targetBundle;
   }

   public void setTargetBundle( Bundle targetBundle ) {
      this.targetBundle = targetBundle;
   }

   public UsesConflictResolutionSuggestion( UsesConflictResolutionSuggestionType type ) {
      this.type = type;
   }

   public UsesConflictResolutionSuggestionType getType() {
      return type;
   }

   @Override
   public String toString() {
      String result = "No Suggestion";
      if( UsesConflictResolutionSuggestionType.UninstallBundle.equals( type ) ) {
         result = String.format( "uninstall %s // %s", targetBundle.getBundleId(), targetBundle.getSymbolicName() );
      }
      else if( UsesConflictResolutionSuggestionType.RefreshBundle.equals( type ) ) {
         result = String.format( "refresh %s // $s", targetBundle.getBundleId(), targetBundle.getSymbolicName() );
      }
      return result;
   }

   @Override
   public boolean equals( Object otherObj ) {
      boolean result = otherObj != null && otherObj.getClass().equals( getClass() );
      if( result ) {
         final UsesConflictResolutionSuggestion other = ( UsesConflictResolutionSuggestion )otherObj;
         result = result && getType().equals( other.getType() );
         if( getTargetBundle() != null ) {
            result = result && getTargetBundle().equals( other.getTargetBundle() );
         }
      }
      return result;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + getClass().hashCode();
      result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
      result = prime * result + ( ( targetBundle == null ) ? 0 : targetBundle.hashCode() );
      return result;
   }
}
