package tools.osgi.maven.integration.internal;

import java.util.Date;

public class Duration {
   private Date startTime;
   private Date endTime;

   public Duration() {}

   public Duration( Date startTime, Date endTime ) {
      setStartTime( startTime );
      setEndTime( endTime );
   }

   public Date getStartTime() {
      return startTime;
   }

   public void setStartTime( Date startTime ) {
      this.startTime = startTime;
   }

   public Date getEndTime() {
      return endTime;
   }

   public void setEndTime( Date endTime ) {
      this.endTime = endTime;
   }

   public Long getTotalSeconds() {
      if( startTime == null ) {
         throw new RuntimeException( "Must specify a start time to calculate seconds" );
      }
      if( endTime == null ) {
         throw new RuntimeException( "Must specify an end time to calculate seconds" );
      }
      return ( endTime.getTime() - startTime.getTime() ) / 1000;
   }

   public Long getTotalMinutes() {
      return getTotalSeconds() > 0 ? getTotalSeconds() / 60 : 0;
   }

   public Long getTotalHours() {
      return getTotalMinutes() > 0 ? getTotalMinutes() / 60 : 0;
   }

   public Long getTotalDays() {
      return getTotalHours() > 0 ? getTotalHours() / 24 : 0;
   }

   public String getFormatted( String description ) {
      return String.format( "%s: %s", description, getFormatted() );
   }

   public Long getFormattedDays() {
      return getTotalDays();
   }

   public Long getFormattedHours() {
      return getTotalHours() - ( getTotalDays() * 24 );
   }

   public Long getFormattedMinutes() {
      return getTotalMinutes() - ( getTotalHours() * 60 );
   }

   public Long getFormattedSeconds() {
      return getTotalSeconds() - ( getTotalMinutes() * 60 );
   }

   public String getFormatted() {
      final StringBuffer result = new StringBuffer();
      boolean useDivider = false;
      if( getFormattedDays() > 0 ) {
         result.append( String.format( "%s Days", getFormattedDays() ) );
         useDivider = true;
      }
      if( getFormattedHours() > 0 ) {
         result.append( useDivider ? ", " : "" );
         result.append( String.format( "%s Hours", getFormattedHours() ) );
         useDivider = true;
      }
      if( getFormattedMinutes() > 0 ) {
         result.append( useDivider ? ", " : "" );
         result.append( String.format( "%s Minutes", getFormattedMinutes() ) );
         useDivider = true;
      }
      if( getFormattedSeconds() > 0 || !useDivider ) {
         result.append( useDivider ? ", " : "" );
         result.append( String.format( "%s Seconds", getFormattedSeconds() ) );
      }
      return result.toString();
   }
}
