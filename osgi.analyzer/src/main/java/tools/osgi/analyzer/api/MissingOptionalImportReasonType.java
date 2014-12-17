package tools.osgi.analyzer.api;

/** Defines the known reasons for optional imports not being resolved on a bundle */
public enum MissingOptionalImportReasonType {
   /** Not Applicable */
   Null,
   /** Import could be resolved but a refresh is required */
   RefreshRequired,
   /** Unknown why the import is not resolved */
   Unknown,
   /** No bundles are available to resolve the import */
   Unavailable,
   /** Import could be resolved but would result in a use conflict */
   UseConflict, ;
}
