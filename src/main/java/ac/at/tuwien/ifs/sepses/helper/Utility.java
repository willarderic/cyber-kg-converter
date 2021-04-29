package ac.at.tuwien.ifs.sepses.helper;

import ac.at.tuwien.ifs.sepses.storage.Storage;
import ac.at.tuwien.ifs.sepses.storage.impl.DummyStorage;
import ac.at.tuwien.ifs.sepses.storage.impl.FusekiStorage;
import ac.at.tuwien.ifs.sepses.storage.impl.VirtuosoStorage;
import ac.at.tuwien.ifs.sepses.vocab.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Utility {

    private static final Logger log = LoggerFactory.getLogger(Utility.class);

    /**
     * check if a graph needs update (based on the catalog iD)
     *
     * @param metaModel
     * @param endpoint
     * @param graph
     * @param catalogResource
     * @return true if existing catalog id the same with the new catalog id
     * @throws IOException
     */
    public static boolean checkIsUpToDate(Model metaModel, String endpoint, String graph, Resource catalogResource)
            throws IOException {

        ParameterizedSparqlString queryString1 =
                new ParameterizedSparqlString("select ?s from ?graph where { ?s a ?catalog }");
        Resource graphResource = ResourceFactory.createResource(graph);
        queryString1.setParam("graph", graphResource);
        queryString1.setParam("catalog", catalogResource);
        QueryExecution qeQuery1 = QueryExecutionFactory.sparqlService(endpoint, queryString1.asQuery());

        ParameterizedSparqlString stringQuery2 = new ParameterizedSparqlString("select ?s where { ?s a ?catalog }");
        stringQuery2.setParam("catalog", catalogResource);
        QueryExecution qeQuery2 = QueryExecutionFactory.create(stringQuery2.asQuery(), metaModel);

        return checkResultContainTheSameValue(qeQuery1, qeQuery2, "?s");
    }

    public static boolean checkIsEqualModifedDate(String tempRML, String xmlFile, String endpoint, String graph,
            Property property) throws IOException {

        ParameterizedSparqlString queryString1 =
                new ParameterizedSparqlString("select ?date from ?graph where { ?s ?property ?date }");
        Resource graphResource = ResourceFactory.createResource(graph);
        queryString1.setParam("graph", graphResource);
        queryString1.setParam("property", property);
        QueryExecution qeQuery1 = QueryExecutionFactory.sparqlService(endpoint, queryString1.asQuery());

        Model TempModel = XMLParser.Parse(xmlFile, tempRML);
        ParameterizedSparqlString stringQuery2 =
                new ParameterizedSparqlString("select ?date where { ?s ?property ?date }");
        stringQuery2.setParam("property", property);
        QueryExecution qeQuery2 = QueryExecutionFactory.create(stringQuery2.asQuery(), TempModel);

        return checkResultContainTheSameValue(qeQuery1, qeQuery2, "?date");
    }

    public static boolean checkResultContainTheSameValue(QueryExecution qeQuery1, QueryExecution qeQuery2,
            String var) {
        ResultSet rs1 = qeQuery1.execSelect();
        String s1 = "";
        while (rs1.hasNext()) {
            QuerySolution qsQuery1 = rs1.nextSolution();
            RDFNode cat = qsQuery1.get(var);
            s1 = cat.toString();
        }
        ResultSet rs2 = qeQuery2.execSelect();
        String s2 = "";
        while (rs2.hasNext()) {
            QuerySolution qsQuery2 = rs2.nextSolution();
            RDFNode cat2 = qsQuery2.get(var);
            s2 = cat2.toString();
        }

        return (s1.equals(s2));
    }

    /**
     * check whether a graph contains instance of a certain class
     *
     * @param endpoint
     * @param CPEGraphName
     * @param cls
     * @return
     */
    public static boolean checkIsGraphNotEmpty(String endpoint, String CPEGraphName, Resource cls) {
        //select if resource is not empty
        ParameterizedSparqlString queryString = new ParameterizedSparqlString("ASK FROM ?graph where { ?a a ?cls }");
        Resource graphResource = ResourceFactory.createResource(CPEGraphName);
        queryString.setParam("graph", graphResource);
        queryString.setParam("cls", cls);

        QueryExecution qeQuery1 = QueryExecutionFactory.sparqlService(endpoint, queryString.asQuery());
        return qeQuery1.execAsk();
    }

    /**
     * Counts how many instances are contained within the downloaded file
     *
     * @param model
     * @param cls
     * @return string of instance count
     */
    public static Integer countInstance(Model model, Resource cls) {
        Integer count = 0;
        ParameterizedSparqlString queryString =
                new ParameterizedSparqlString("select (str(count(?s)) as ?c) where { ?s a ?cls }");
        queryString.setParam("cls", cls);
        QueryExecution queryExecution = QueryExecutionFactory.create(queryString.asQuery(), model);

        ResultSet resultSet = queryExecution.execSelect();
        while (resultSet.hasNext()) {
            QuerySolution querySolution = resultSet.nextSolution();
            RDFNode queryNode = querySolution.get("c");
            count = queryNode.asLiteral().getInt();
        }
        return count;

    }

    /**
     * Counts how many instances are contained within a named graph in a sparql endpoint
     *
     * @param endpoint
     * @param cls
     * @return string of instance count
     */
    public static Integer countInstance(String endpoint, String graph, Resource cls) {
        Integer count = 0;
        ParameterizedSparqlString queryString =
                new ParameterizedSparqlString("select (str(count(?s)) as ?c) FROM ?graph where { ?s a ?cls }");
        Resource graphResource = ResourceFactory.createResource(graph);
        queryString.setParam("cls", cls);
        queryString.setParam("graph", graphResource);

        QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpoint, queryString.asQuery());

        ResultSet resultSet = queryExecution.execSelect();
        while (resultSet.hasNext()) {
            QuerySolution querySolution = resultSet.nextSolution();
            RDFNode queryNode = querySolution.get("c");
            count = queryNode.asLiteral().getInt();
        }
        return count;

    }

    public static String saveToFile(Model model, String outputDir, String xmlURL) {

        String fileName = xmlURL.substring(xmlURL.lastIndexOf("/") + 1);
        if (fileName.indexOf("\\") >= 0) {
            fileName = xmlURL.substring(xmlURL.lastIndexOf("\\") + 1);
        }
        String outputFileName = outputDir + "/" + fileName + "-output.ttl";
        File outputFile = new File(outputFileName);
        outputFile.getParentFile().mkdirs();

        try {
            FileWriter out = new FileWriter(outputFile);
            model.write(out, "TURTLE");
            out.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return outputFileName;
    }

    public static Storage getStorage(Properties properties) {
        String triplestore = properties.getProperty("Triplestore");
        if (triplestore.equalsIgnoreCase("fuseki")) {
            log.info("Fuseki triplestore is selected");
            return FusekiStorage.getInstance(properties.getProperty("FusekiBinariesDir"));
        } else if (triplestore.equalsIgnoreCase("virtuoso")) {
            log.info("Virtuoso triplestore is selected");
            return VirtuosoStorage.getInstance();
        } else if (triplestore.equalsIgnoreCase("dummy")) { // for testing/benchmarking purpose
            log.info("Dummy triplestore is selected");
            return DummyStorage.getInstance();
        } else {
            log.error("Triplestore type is not supported !!!");
            return null;
        }
    }

    public static Model validateWithShacl(String shaclFile, Model model) {

        Model constraints = ModelFactory.createDefaultModel();
        InputStream is = Utility.class.getClassLoader().getResourceAsStream(shaclFile);
        RDFDataMgr.read(constraints, is, Lang.TURTLE);
        Resource result = ValidationUtil.validateModel(model, constraints, false);

        return result.getModel();
    }

    public static Map<String, String> getPrefixes() {
        Map<String, String> prefixes = new HashMap<>();

        // General
        prefixes.put("rdf", RDF.NAMESPACE);
        prefixes.put("rdfs", RDFS.NAMESPACE);
        prefixes.put("owl", OWL.NS);
        prefixes.put("dct", DCTerms.NS);

        // SEPSES classes
        prefixes.put("cpe", CPE.NS);
        prefixes.put("cve", CVE.NS);
        prefixes.put("cvss", CVSS.NS);
        prefixes.put("capec", CAPEC.NS);
        prefixes.put("cwe", CWE.NS);

        // SEPSES resources
        prefixes.put("cpe-res", CPE.NS_INSTANCE);
        prefixes.put("cve-res", CVE.NS_INSTANCE);
        prefixes.put("cvss-res", CVSS.NS_INSTANCE);
        prefixes.put("capec-res", CAPEC.NS_INSTANCE);
        prefixes.put("cwe-res", CWE.NS_INSTANCE);

        return prefixes;
    }
}
