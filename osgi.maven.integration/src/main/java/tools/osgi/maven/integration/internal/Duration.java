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

   public String getFormatted() {
      final StringBuffer result = new StringBuffer();
      result.append( getTotalDays() > 0 ? String.format( "%s Days", getTotalDays() ) : "" );
      result.append( getTotalHours() > 0 && getTotalDays() > 0 ? ", " : "" );
      result.append( getTotalHours() > 0 ? String.format( "%s Hours", getTotalHours() ) : "" );
      result.append( getTotalMinutes() > 0 && getTotalHours() > 0 ? ", " : "" );
      result.append( getTotalMinutes() > 0 ? String.format( "%s Minutes", getTotalMinutes() ) : "" );
      result.append( getTotalMinutes() > 0 ? ", " : "" );
      result.append( String.format( "%s Seconds", getTotalSeconds() ) );
      return result.toString();
   }
}
