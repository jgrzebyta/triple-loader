(ns rdf4j.core.rio
  "This namespace holds constants with:
  
      - pre-configured instances of configurators for rio writer and rio parser
      - map format name with `org.eclipse.rdf4j.rio.RDFFormat` object"
  (:import [org.eclipse.rdf4j.rio RDFFormat ParserConfig WriterConfig]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings BasicWriterSettings]))


(def map-formats
  "Maps RDF format name with relevant instance of `org.eclipse.rdf4j.rio.RDFFormat`."
  {"ntriples" RDFFormat/NTRIPLES
   "n3" RDFFormat/N3
   "turtle" RDFFormat/TURTLE
   "rdfjson" RDFFormat/RDFJSON
   "rdfxml"RDFFormat/RDFXML
   "trig" RDFFormat/TRIG
   "trix" RDFFormat/TRIX
   "nquads" RDFFormat/NQUADS
   "jsonld" RDFFormat/JSONLD
   "binary" RDFFormat/BINARY})

(def default-parser-config
  "Pre-configured instance of `org.eclipse.rdf4j.rio.ParserConfig`."
  (doto
      (ParserConfig.)
    (.set BasicParserSettings/NORMALIZE_DATATYPE_VALUES true)
    (.set BasicParserSettings/PRESERVE_BNODE_IDS true)
    (.set BasicParserSettings/VERIFY_URI_SYNTAX true)))

(def default-pp-writer-config
  "Pre-configured instance of `org.eclipse.rdf4j.rio.WriterConfig`"
  (doto
      (WriterConfig.)
    (.set BasicWriterSettings/PRETTY_PRINT true)
    (.set BasicWriterSettings/XSD_STRING_TO_PLAIN_LITERAL false)
    (.set BasicWriterSettings/INLINE_BLANK_NODES true)))
