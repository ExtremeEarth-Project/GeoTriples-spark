package eu.linkedeodata.geotriples.Converters;

import be.ugent.mmlab.rml.core.Config;
import be.ugent.mmlab.rml.core.NodeRMLPerformer;
import be.ugent.mmlab.rml.function.*;
import be.ugent.mmlab.rml.model.*;
import be.ugent.mmlab.rml.processor.concrete.RowProcessor;
import eu.linkedeodata.geotriples.utils.NTriplesAlternative;
import org.apache.spark.sql.Row;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFHandlerException;
import java.util.*;



/**
 * Perform the conversion of data into RDF  using RML.
 */
public class RML_Converter implements java.io.Serializable {

    private ArrayList<TriplesMap> mappingList;
    private List<List<PredicateObjectMap>> listPOM;
    private List<List<URI>> tm_predicates;
    private NTriplesAlternative rdfWriter;
    private RowProcessor processor;
    private NodeRMLPerformer performer;


    /**
     * Getters
     */

    public List<List<PredicateObjectMap>> getListPOM() { return listPOM; }

    public RowProcessor getProcessor() { return processor; }

    public List<List<URI>> getTm_predicates() { return tm_predicates; }

    /**
     * Constructor.
     *
     * @param mapping_list: the TriplesMap as was parsed by the input RML file.
     * @param headers the headers of the expected rows.
     */
    public RML_Converter(ArrayList<TriplesMap> mapping_list, List<String> headers) {
        registerFunctions();
        Config.EPSG_CODE = 4326;
        mappingList = mapping_list;

        processor = new RowProcessor();
        processor.setFields(headers);
        performer = new NodeRMLPerformer(processor);

        listPOM = new ArrayList<>();
        tm_predicates = new ArrayList<>();
        try {
            for (TriplesMap tm : mappingList) {
                processor.createSubjectsTemplate(tm);
                List<PredicateObjectMap> list_pom = new ArrayList<>(tm.getPredicateObjectMaps());
                List<URI> predicates_list = new ArrayList<>();
                for (PredicateObjectMap pom : list_pom) {
                    for (PredicateMap pm : pom.getPredicateMaps())
                        predicates_list.add(processor.RowProcessPredicateMap(pm, null));
                    for(ObjectMap obj_map : pom.getObjectMaps())
                        processor.createObjectsTemplate(obj_map);
                }
                tm_predicates.add(predicates_list);
                listPOM.add(list_pom);
            }
        }catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
        rdfWriter = new NTriplesAlternative();
    }


    /**
     * Construct an RML_Converter by initializing its fields with pre-constructed variables.
     * It is used in order to create a copy of an existing RML_Converter.
     *
     * @param mapping_list input mappings
     * @param list_pom input list_pom
     * @param tmPredicates input tm_predicates
     * @param in_headers row processor's headers
     * @param subjTemplates row processor's subject  templates
     * @param subjTokens row processor's subject tokens
     * @param subjReplacements_pos row processor's sublect replacements
     * @param objTemplates row processor's object templates
     * @param objTokens row processor's object tokens
     * @param objReplacements_pos row processor's object replacements
     * @param objTemplatesMap row processor's object templates map
     */
    public RML_Converter(ArrayList<TriplesMap> mapping_list, List<List<PredicateObjectMap>> list_pom, List<List<URI>> tmPredicates,
                         Set<String> in_headers, List<String> subjTemplates, List<List<String>> subjTokens
                        ,List<List<String>> subjReplacements_pos, List<String> objTemplates, List<List<String>> objTokens
                        ,List<List<String>> objReplacements_pos,  Map<String, Integer> objTemplatesMap){

        registerFunctions();
        Config.EPSG_CODE = 4326;
        mappingList = mapping_list;

        processor = new RowProcessor(in_headers, subjTemplates, subjTokens, subjReplacements_pos, objTemplates
                ,objTokens, objReplacements_pos,  objTemplatesMap);
        performer = new NodeRMLPerformer(processor);

        listPOM = list_pom;
        tm_predicates = tmPredicates;

        rdfWriter = new NTriplesAlternative();
    }



    /**
     * Convert a Spark Partition into triples.
     *
     * @param partition_iter: an iterator of rows. The rows must follow the predefined headers.
     * @return a String of triples
     */
    public Iterator<String> convertPartition(Iterator<Row> partition_iter){

        List<String> partitionTriples = new LinkedList<>();
        rdfWriter.startRDF();
        partition_iter.forEachRemaining(row -> {
            try {
                for (int i = 0; i < mappingList.size(); i++) {
                    rdfWriter.handleStatementIter(performer.perform(row, mappingList.get(i), tm_predicates.get(i), listPOM.get(i), processor, i));
                }
                partitionTriples.add(rdfWriter.getString());

            }
            catch (RDFHandlerException e) {
                System.out.println("ERROR while Handling Statement");
                e.printStackTrace();
                System.exit(1);
            }
        });
        rdfWriter.endRDF();
        return  partitionTriples.iterator();
    }


    /**
     * Convert a Spark Row into triples.
     * @param row the input row.
     * @returna String of triples
     */
   public String convertRow(Row row){
        String triples = null;
        //rdfWriter = new NTriplesAlternative();
        try {
            rdfWriter.startRDF();
            for (int i = 0; i < mappingList.size(); i++)
                rdfWriter.handleStatementIter(performer.perform(row, mappingList.get(i), tm_predicates.get(i), listPOM.get(i), processor, i));
            triples = rdfWriter.getString();
            rdfWriter.endRDF();
        }
        catch (RDFHandlerException e) {
           System.out.println("ERROR while Handling Statement");
           e.printStackTrace();
           System.exit(1);
       }
        return triples;

   }

    /**
     * Register functions.
     */
    private static void registerFunctions()   {
        try {
            // If a JVM extractor handles more tha one partition then each function must be registered once
            URI func_equi = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/equi");
            URI func_asWKT = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/asWKT");
            URI func_hasSerialization = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/hasSerialization");
            URI func_asGML = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/asGML");
            URI func_isSimple = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/isSimple");
            URI func_isEmpty = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/isEmpty");
            URI func_is3D = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/is3D");
            URI func_spatialDimension = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/spatialDimension");
            URI func_dimension = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/dimension");
            URI func_coordinateDimension = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/coordinateDimension");
            URI func_area = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/area");
            URI func_length = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/length");
            URI func_centroidx = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/centroidx");
            URI func_centroidy = new URIImpl("http://www.w3.org/ns/r2rml-ext/functions/def/centroidy");

            if (! FunctionFactory.availableFunctions.containsKey(func_equi))
                FunctionFactory.registerFunction(func_equi, new FunctionEQUI());

            if (! FunctionFactory.availableFunctions.containsKey(func_asWKT))
                FunctionFactory.registerFunction(func_asWKT, new FunctionAsWKT());

            if (! FunctionFactory.availableFunctions.containsKey(func_hasSerialization))
                FunctionFactory.registerFunction(func_hasSerialization, new FunctionHasSerialization());

            if (! FunctionFactory.availableFunctions.containsKey(func_asGML))
                FunctionFactory.registerFunction(func_asGML, new FunctionAsGML());

            if (! FunctionFactory.availableFunctions.containsKey(func_isSimple))
                FunctionFactory.registerFunction(func_isSimple, new FunctionIsSimple());

            if (! FunctionFactory.availableFunctions.containsKey(func_isEmpty))
                FunctionFactory.registerFunction(func_isEmpty, new FunctionIsEmpty());

            if (! FunctionFactory.availableFunctions.containsKey(func_is3D))
                FunctionFactory.registerFunction(func_is3D, new FunctionIs3D());

            if (! FunctionFactory.availableFunctions.containsKey(func_spatialDimension))
                FunctionFactory.registerFunction(func_spatialDimension, new FunctionSpatialDimension());
            
            if (! FunctionFactory.availableFunctions.containsKey(func_dimension))
                FunctionFactory.registerFunction(func_dimension, new FunctionDimension());

            if (! FunctionFactory.availableFunctions.containsKey(func_coordinateDimension))
                FunctionFactory.registerFunction(func_coordinateDimension, new FunctionCoordinateDimension());

            if (! FunctionFactory.availableFunctions.containsKey(func_area))
                FunctionFactory.registerFunction(func_area, new FunctionArea());

            if (! FunctionFactory.availableFunctions.containsKey(func_length))
                FunctionFactory.registerFunction(func_length, new FunctionLength());

            if (! FunctionFactory.availableFunctions.containsKey(func_centroidx))
                FunctionFactory.registerFunction(func_centroidx, new FunctionCentroidX());

            if (! FunctionFactory.availableFunctions.containsKey(func_centroidy))
                FunctionFactory.registerFunction(func_centroidy, new FunctionCentroidY());

        } catch (FunctionAlreadyExists functionAlreadyExists) {
            //functionAlreadyExists.printStackTrace();
        }
    }
}
