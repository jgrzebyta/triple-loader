(ns rdf4j.core.rio
  (:import [org.eclipse.rdf4j.rio RDFFormat ParserConfig WriterConfig]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings BasicWriterSettings]))


(def map-formats {"ntriples" RDFFormat/NTRIPLES
                  "n3" RDFFormat/N3
                  "turtle" RDFFormat/TURTLE
                  "rdfjson" RDFFormat/RDFJSON
                  "rdfxml"RDFFormat/RDFXML
                  "trig" RDFFormat/TRIG
                  "trix" RDFFormat/TRIX
                  "nquads" RDFFormat/NQUADS
                  "jsonld" RDFFormat/JSONLD
                  "binary" RDFFormat/BINARY})

(def default-parser-config (doto
                               (ParserConfig.)
                             (.set BasicParserSettings/NORMALIZE_DATATYPE_VALUES true)
                             (.set BasicParserSettings/PRESERVE_BNODE_IDS true)
                             (.set BasicParserSettings/VERIFY_URI_SYNTAX true)))

(def default-pp-writer-config (doto
                                  (WriterConfig.)
                                (.set BasicWriterSettings/PRETTY_PRINT true)
                                (.set BasicWriterSettings/XSD_STRING_TO_PLAIN_LITERAL false)
                                (.set BasicWriterSettings/INLINE_BLANK_NODES true)))
