package opendap.wcs.v2_0;

import net.opengis.gml.v_3_2_1.*;
import net.opengis.wcs.v_2_0.CoverageDescriptionType;
import opendap.dap4.*;
import opendap.namespaces.XML;
import opendap.threddsHandler.ThreddsCatalogUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.util.*;

public class DynamicCoverageDescription extends CoverageDescription {
    private Logger _log;
    private Element _myDMR;

    public DynamicCoverageDescription() {
        super();
        _log = LoggerFactory.getLogger(getClass());
        _myDMR = null;
    }

    /**
     * Primary constructor for this class
     *
     * @param dmr
     * @throws IOException
     */
    public DynamicCoverageDescription(Element dmr) throws IOException, WcsException {
        this();
        _myDMR = dmr;
        // TODO: Get the dataset URL from the DMR top level attribute "xml:base"
        String datasetUrl = dmr.getAttributeValue("base", XML.NS);
        setDapDatasetUrl(new URL(datasetUrl));

        /** - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
         *  TODO:  Replace this stuff with the output of WcsMarchaller
         */

        ingestDmr(dmr);

        if (_myCD == null) {
            _myCD = new Element("CoverageDescription", WCS.WCS_NS);
            Element coverageId = new Element("CoverageId", WCS.WCS_NS);
            String name = _myDMR.getAttributeValue("name");
            coverageId.setText(name);
            _myCD.addContent(coverageId);
        }

        /** - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

        //TODO: All this stuff needs reviewed?
        ////////////////////////////////////
        //  use DOM to directly set member variables

        //_myFile = cd._myFile.getCanonicalFile();
        //_validateContent = cd._validateContent;
        //_dapGridId.putAll(cd._dapGridId);
        //_domainCoordinates.putAll(dataset.getDimensions());
        //this.fields = dataset.getVars32bitFloats();
        //_initialized = cd._initialized;

        /**
         * TODO: Populate the parent class's (CoverageDescription) internal objects including: _myCD, _gridIds, _domainCoordinates, and _fields from WcsMarchaller
         * The parent class may need additional setter/getters or protected variables
         * in order to fufill this.
         */

    }

    /**
     * Abandon this version for the version that takes the DMR as a JDOM element (see above)
     * <p>
     * From dap4dmr branch version of CoverageDescription
     *
     * @param datasetUrl
     * @throws IOException
     */
    @Deprecated
    public DynamicCoverageDescription(String datasetUrl) throws IOException, JDOMException, WcsException {
        super();
        setDapDatasetUrl(new URL(datasetUrl));

        String dmrUrl = datasetUrl + ".dmr.xml";
        Element dmr = opendap.xml.Util.getDocumentRoot(dmrUrl);

        ingestDmr(dmr);

        ////////////////////////////////////
        //  use DOM to directly set member variables

        //_myFile = cd._myFile.getCanonicalFile();
        //_validateContent = cd._validateContent;
        //_dapGridId.putAll(cd._dapGridId);
        //_domainCoordinates.putAll(dataset.getDimensions());
        //this.fields = dataset.getVars32bitFloats();
        //_initialized = cd._initialized;

    }

    private void ingestDmr(Element dmr) throws WcsException {

        /////////////////////////////////////////////////
        // Use OLFS method to fetch the DMR
        //
        // For this to work, against a NASA server
        // then you will need to have valid
        // Earthdata Login credentials available in the
        // local filesystem like so:
        // In Unix ~/.netrc should have
        // machine urs.earthdata.nasa.gov
        // login userName
        // password password
        //
        // Then login to earthdata and add the respective
        // dataset to user profile

        // Object wrapper to DMR XML

        JAXBContext jc = null;
        Dataset dataset = null;
        try {
            jc = JAXBContext.newInstance(Dataset.class);
            Unmarshaller um = jc.createUnmarshaller();
            XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
            String dmrXml = xmlo.outputString(dmr);
            InputStream is = new ByteArrayInputStream(dmrXml.getBytes("UTF-8"));
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader xsr = factory.createXMLStreamReader(is);
            XMLReaderWithNamespaceInMyPackageDotInfo xr = new XMLReaderWithNamespaceInMyPackageDotInfo(xsr);
            dataset = (Dataset) um.unmarshal(xr);
        } catch (JAXBException | UnsupportedEncodingException | XMLStreamException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to build Dataset instance from JDOM DMR document.");
            sb.append(" Caught ").append(e.getClass().getName());
            sb.append(" Message  ").append(e.getMessage());
            _log.error(sb.toString());
            throw new WcsException(sb.toString(), WcsException.NO_APPLICABLE_CODE);
        }

        /////////////////////////////////////////////////////////////////////////
        // interpret contents of the dataset (DMR) to generate WCS per OGC below.

        // First Loop through the variables to glean "knowledge"

        // FIXME Test the null condition above in the try/catch block and exit there
        // with an exception the WCS service code can catch and process. Doing that
        // will also reduce one level of nesting down here. jhrg 9/7/17
        if (dataset == null) {
            _log.debug("DMR dataset....NULL; bye-bye");
            throw new WcsException("Failed to build Dataset instance from DMR.", WcsException.NO_APPLICABLE_CODE);
        }
        //////////////////////////////////////////////////////////
        // this else block extends all the way to almost
        // end of program with brace commented with }
        // end if (dataset==null)

        // FIXME If you need this, put it in a method and call it when the logger is
        // set to the DEBUG level. ... Sanity check, too. jhrg 9/7/17

        _log.debug("Marshalling WCS from DMR at Url: {}", dataset.getUrl());

        ////////////////////////////////////////////////
        // Sanity Test
        // Create the Document
                /* FIXME: The code in comment block doesn't work but should. It complains that saxon9-dom.jar is not available. Fix or eliminate.
                DocumentBuilderFactory dbf0 = DocumentBuilderFactory.newInstance();
                DocumentBuilder db0 = dbf0.newDocumentBuilder();
                Document document0 = db0.newDocument();

                // Marshal the Object to a Document
                Marshaller m0 = jc.createMarshaller();
                m0.marshal(dataset, document0);

                // Output the Document:
                // runtime dependence on saxon9-jdom.jar library
                // OK...since this is just sanity check
                // all we really need is the DOM which has already been marshaled above
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer t = tf.newTransformer();
                DOMSource source = new DOMSource(document0);
                StreamResult result = new StreamResult(System.out);
                t.transform(source, result);
                */
        // End Sanity Test

        ////////////////////////////
        // echo data set Dimensions - distinct from variable dimension


        /* NOTE: This is the old loop iterator pattern which I have replaced
           with the for() loop below. By creating default contrsutors in all the
           related classes we can ensure that none of the getter methods return null.
           And this frees us to use simpler code. See below

            List<Dimension> dimensions = dataset.getDimensions();

            if (dimensions == null) {
                _log.debug("Dimensions List NULL");
            } else if (dimensions.size() == 0) {
                _log.debug("Dimensions list EMPTY");
            } else {
                ListIterator dimIter = dimensions.listIterator();
                // we know it is non-null and non-empty, so safe to do this right away
                while (dimIter.hasNext()) {
                    _log.debug(dimIter.nextIndex() + ". " + dimIter.next());
                }
            } // close if then else (dimensions list is non-null and has elements)

        */

        // Simpler iteration over content.
        for (Dimension dim : dataset.getDimensions()) {
            _log.debug(dim.toString());

        }

        /////////////////////////////////////////////////////////
        // Process the DAP variables found in the DMR.
        // This means determine if the DAP var is a field, and then
        // make the appropriate associates in the member variables.
        for (Float32 var : dataset.getVars32bitFloats()) {

        for(Float32 var : dataset.getVars32bitFloats()){
            ingestDapVar(var);
        }
        for (Float64 var : dataset.getVars64bitFloats()) {
            ingestDapVar(var);
        }
        for (Int32 var : dataset.getVars32bitIntegers()) {
            ingestDapVar(var);
        }
        for (Int64 var : dataset.getVars64bitIntegers()) {
            ingestDapVar(var);
        }

        /////////////////////////////////////////////////////////////////
        // echo "container" attributes and, yes, attributes of attributes
        // these attributes and *their* inner attributes (yes they are nested)
        // will later need to be sniffed for exceptions before marshalling WCS
        // per Nathan's heauristic

        boolean foundConvention = false;
        boolean cfCompliant = false;
        for (ContainerAttribute containerAttribute : dataset.getAttributes()) {
            boolean foundGlobal = false;
            _log.debug(containerAttribute.toString());

            String ca_name = containerAttribute.getName();
            if (ca_name.toLowerCase().contains("convention")) {
                _log.debug("Found container attribute named convention(s)");
                foundConvention = true;
            } // this will find plural conventions
            else if (ca_name.toLowerCase().endsWith("_global") || ca_name.equalsIgnoreCase("DODS_EXTRA")) {
                _log.debug("Found container attribute name ending in _GLOBAL or DODS_EXTRA");
                _log.debug("Looking for conventions...attribute");
                foundGlobal = true;
            }

            // now enumerate all attributes of the "container" attribute

            for (Attribute a : containerAttribute.getAttributes()) {
                _log.debug(a.toString());

                if (foundGlobal) {
                    // test for conventions
                    String a_name = a.getName();

                    if (a_name == null || a_name.trim().length() == 0) {
                        // no action
                        _log.debug("Attribute has no name??");
                    } else if (a_name.toLowerCase().contains("convention")) {
                        foundConvention = true;
                        String a_value = a.getValue();
                        _log.debug(
                                "Found attribute named convention(s), value = " + a_value);

                        if (a_value.toLowerCase().contains("cf-")) {
                            cfCompliant = true;
                            _log.debug("Dataset is CF Compliant!!");
                        }

                    }
                } // end Found Global, now look at its attributes
            }

            if (foundConvention) {
                if (cfCompliant) {
                    // already announced success
                } else {
                    _log.debug("Found GLOBAL Convention but may not be CF compliant...ERROR");
                }
            } else {
                _log.debug("No conventions found...ERROR");
            }

        } // end if (dataset == null) else { // do everything

        //////////////////////////////////////////////////
        // Start WCS CoverageDescription..
        // the OLFS functions from a JDOM wrapper to this

        net.opengis.wcs.v_2_0.CoverageDescriptionType cd = new net.opengis.wcs.v_2_0.CoverageDescriptionType();

        // this will create id as element
        cd.setCoverageId(dataset.getCoverageId());
        // this will create id as attribute
        cd.setId(dataset.getCoverageId());

        ////////////////////////////////////////////////////////////////////////////////////
        // Envelope with Time Period:
        // lowerCorner, UpperCorner and Time Position
        // The ability to reflect time period is most important difference between WCS and other mapping services like WFS
        // May or may not find these in the attributes in DMR
        // look for camelCase NorthernMostLatitude, SouthernMostLatitude etc - in global attribute in the bottom of the file
        // not CF- names (Nathan: too bad we do not have the range function rolled out yet, but when we do will be helpful)
        // look at netCDF attribute convention for dataset discovery page (out-of-date and/or may be subsumed into CF-16)
        // while "SouthernMostLatitude" doed not exist in any standard..."SouthernMost [space] Latitude" does ... in some form
        // Also: instead of lookingt at the attributes (metadata), can perhaps derive from dataset values themselves...may be too complicated
        // so...for version #1: we will NOT look at anywhere other than attributes to get...this spatial temporal envelope
        // also punt on the SRS - use the entire global if nothing else specified.
        // Build up a list of candidate attribute names, look for this in particular variable and also the global attributes
        // Variables have shared dimensions specified, but defined typically at the top of the dataset, but could be referencing somewhere else
        // Absolutely can trust what is in the DMR, but instead of repeating - they shove them at the end as global attributes
        // There are other reasons for being global as well. For instance everyone of these variables have the same longitude and latitude
        // so instead of repeating, they just put them down with global specs.
        // Hence the RULE: look into local variable for lat/long of envelope, and barring that look in the global attributes, and if not even there skip envelope definition
        // Look for any attribute whose name contains "Southern", "Southern Most" - pretty hokey!
        // Look at container - NC_GLOBAL.  Value of conventions is CF-1.  For all practical - standard.
        //
        // For time - compute timestamp using minutes value since 1980. Hard to suss out but doable.  Formulate a DAP URL and request that.
        // WHOLE LOT MORE RELIABLE than parsing attributes (which could just be wrong): reading data from the file DAP style for lat, long of envelope as well.
        // Can write quick test for monotonic increasing, rectified grid etc.
        // should we make new server function - rather than doing java.  Range along will be a huge improvement.
        // RULE for "what is the envelope?" would be to use does-not-quite exist yet RANGE function and possibly other server functions
        // so...very little point in doing this in java - since this too far from the data itself.
        // There is a range function in server - in master branch.  "function_result_fnoc1.nc" - totally works. Not in testbest13 yet. produces illegal output, buggy(?)
        // For range (will not fix now), BOTTOM LINE, do multiple invocations, get values back ... and do stuff
        //  get them back as data, or ASCII and parse it
        // James: You can EITHER formulate a URL that calls this range function to return a DAP dataset, extract value of DAP dataset and work with them, OR
        // OR get ASCII (advantage being no need for importing DAP classes to construct DAP Data Response object...this is the way to go since not big)
        // For time, need to know it is one-dimensional, get a Float 64 number - minutes since - ISO string - need to further process
        //

        // EnvelopeWithTimePeriodType is part of GML
        net.opengis.gml.v_3_2_1.EnvelopeWithTimePeriodType envelope = new net.opengis.gml.v_3_2_1.EnvelopeWithTimePeriodType();
        ///////////////////////////////////////////////////////////////////////////////////////////////
        // default to EPSG 4326 - or WGS 84 - or use "SRS" instead of "CRS"
        // both are equivalent spatial reference systems for the ENTIRE globe
        envelope.setSrsName("urn:ogc:def:crs:EPSG::4326");

        List<String> axisLabelsAsList = Arrays.asList("latitude longitude");
        envelope.setAxisLabels(axisLabelsAsList);

        List<String> uomLabelsAsList = Arrays.asList("deg deg");
        envelope.setUomLabels(uomLabelsAsList);

        envelope.setSrsDimension(new BigInteger("2"));

        net.opengis.gml.v_3_2_1.DirectPositionType envelopeLowerCorner = new net.opengis.gml.v_3_2_1.DirectPositionType();
        List<Double> lowerCorner = Arrays.asList(new Double("-90.00"), new Double("-180.00"));
        envelopeLowerCorner.setValue(lowerCorner);
        envelope.setLowerCorner(envelopeLowerCorner);

        DirectPositionType envelopeUpperCorner = new DirectPositionType();
        List<Double> upperCorner = Arrays.asList(new Double("+90.00"), new Double("+179.375"));
        envelopeUpperCorner.setValue(upperCorner);
        envelope.setUpperCorner(envelopeUpperCorner);

        TimePositionType beginTimePosition = new TimePositionType();
        // attribute called frame seems like right place to put ISO-8601 timestamp
        String beginTimeStr = "2016-01-01T00:30:00.000Z";
        beginTimePosition.setFrame(beginTimeStr);
        // However, it can also be specified as below.
        List<String> timeStrings = Arrays.asList(beginTimeStr);
        beginTimePosition.setValue(timeStrings);
        envelope.setBeginPosition(beginTimePosition);

        TimePositionType endTimePosition = new TimePositionType();
        String endTimeStr = "2016-02-01T00:00:00.000Z";
        endTimePosition.setFrame(endTimeStr);
        // However, it can also be specified as below.
        timeStrings = Arrays.asList(beginTimeStr);
        endTimePosition.setValue(timeStrings);
        envelope.setEndPosition(endTimePosition);

        // it is obvious from method signature of setBoundedBy in
        // CoverageDescription(cd) that BoundingShapeType is
        // needed as argument. It is just an thin-wrapper to the
        // more substantial EnvelopwithTimePeriod object
        net.opengis.gml.v_3_2_1.BoundingShapeType bs = new net.opengis.gml.v_3_2_1.BoundingShapeType();

        // this is GENIUS!!...Object factory...could possibly be used for others
        net.opengis.gml.v_3_2_1.ObjectFactory gmlFactory = new net.opengis.gml.v_3_2_1.ObjectFactory();
        bs.setEnvelope(gmlFactory.createEnvelopeWithTimePeriod(envelope));
        // the factory takes care of this...
        // bs.setEnvelope(new JAXBElement(new QName("http://www.opengis.net/gml/3.2",
        // "EnvelopeWithTimePeriod"), envelope.getClass(),envelope));

        cd.setBoundedBy(bs);

        ///////////////
        // domain set

        net.opengis.gml.v_3_2_1.DomainSetType domainSet = new net.opengis.gml.v_3_2_1.DomainSetType();
        net.opengis.gml.v_3_2_1.RectifiedGridType rectifiedGrid = new net.opengis.gml.v_3_2_1.RectifiedGridType();
        rectifiedGrid.setDimension(new BigInteger("2"));
        rectifiedGrid.setId("Grid-MERRA2_200.inst1_2d_asm_Nx.19920123.nc4");

        // Create the grid envelope for the limits
        GridEnvelopeType gridEnvelope = gmlFactory.createGridEnvelopeType();
        List<BigInteger> lowerRight = Arrays.asList(BigInteger.valueOf(361), BigInteger.valueOf(576));
        List<BigInteger> upperLeft = Arrays.asList(BigInteger.ZERO, BigInteger.ZERO);
        gridEnvelope.withHigh(lowerRight).withLow(upperLeft);
        // Create the limits, set the envelope on them.
        GridLimitsType gridLimits = gmlFactory.createGridLimitsType();
        gridLimits.withGridEnvelope(gridEnvelope);
        rectifiedGrid.setLimits(gridLimits);

        List<String> axisLabels = Arrays.asList("time latitude longitude");
        rectifiedGrid.setAxisLabels(axisLabels);

        // Create the Origin.
        DirectPositionType position = gmlFactory.createDirectPositionType();
        position.withValue(-90.0, -180.0);
        PointType point = gmlFactory.createPointType();
        point.withPos(position);
        point.setId("GridOrigin-MERRA2_200.inst1_2d_asm_Nx.19920123.nc4");
        point.setSrsName("http://www.opengis.net/def/crs/EPSG/0/4326");
        PointPropertyType origin = gmlFactory.createPointPropertyType();
        origin.withPoint(point);
        rectifiedGrid.setOrigin(origin);

        // Create the offset vector.
        List<VectorType> offsetList = new ArrayList<VectorType>();
        VectorType offset1 = gmlFactory.createVectorType();
        offset1.withValue(0.5, 0.0);
        offset1.setSrsName("http://www.opengis.net/def/crs/EPSG/0/4326");
        offsetList.add(offset1);
        VectorType offset2 = gmlFactory.createVectorType();
        offset2.withValue(0.0, 0.625);
        offset2.setSrsName("http://www.opengis.net/def/crs/EPSG/0/4326");
        offsetList.add(offset2);
        rectifiedGrid.setOffsetVector(offsetList);

        domainSet.setAbstractGeometry(gmlFactory.createRectifiedGrid(rectifiedGrid));
        // equivalent to: domainSet.setAbstractGeometry(new JAXBElement(new
        // QName("http://www.opengis.net/gml/3.2", "RectifiedGrid"),
        // rectifiedGrid.getClass(),rectifiedGrid));
        cd.setDomainSet(gmlFactory.createDomainSet(domainSet));
        // equivalent to: cd.setDomainSet(new JAXBElement(new
        // QName("http://www.opengis.net/gml/3.2", "domainSet"),
        // domainSet.getClass(),domainSet));

        ///////////////
        // Range Type
        //
        net.opengis.swecommon.v_2_0.ObjectFactory sweFactory = new net.opengis.swecommon.v_2_0.ObjectFactory();
        net.opengis.swecommon.v_2_0.DataRecordPropertyType rangeType = new net.opengis.swecommon.v_2_0.DataRecordPropertyType();

        // first data record
        net.opengis.swecommon.v_2_0.DataRecordType dataRecord1 = new net.opengis.swecommon.v_2_0.DataRecordType();
        net.opengis.swecommon.v_2_0.DataRecordType.Field dataRecord1Field = new net.opengis.swecommon.v_2_0.DataRecordType.Field();
        net.opengis.swecommon.v_2_0.QuantityType dataRecord1FieldQuantity = new net.opengis.swecommon.v_2_0.QuantityType();

        dataRecord1FieldQuantity.setDefinition("urn:ogc:def:dataType:OGC:1.1:measure");
        dataRecord1FieldQuantity.setDescription("large_scale_rainfall");
        // dataRecord1FieldQuantity.setId("large_scale_rainfall");

        net.opengis.swecommon.v_2_0.UnitReference dataRecord1FieldQuantityUom = new net.opengis.swecommon.v_2_0.UnitReference();
        dataRecord1FieldQuantityUom.setCode("kg m-2 s-1");
        dataRecord1FieldQuantity.setUom(dataRecord1FieldQuantityUom);

        net.opengis.swecommon.v_2_0.AllowedValuesPropertyType dataRecord1FieldQuantityAllowedValues = new net.opengis.swecommon.v_2_0.AllowedValuesPropertyType();
        net.opengis.swecommon.v_2_0.AllowedValuesType allowed1 = new net.opengis.swecommon.v_2_0.AllowedValuesType();

        // NASA guys - Plus or Minus Fill values. Vmin, Vmax. Reasonable gambit - to ask
        // variable for max and min. Say - give me range for hour, no rain?
        // get actual max and min.
        // http://testbed-13.opendap.org:8080/opendap/testbed-13/M2SDNXSLV.5.12.4/MERRA2_100.statD_2d_slv_Nx.19800101.SUB.nc4.ascii?range(HOURNORAIN)
        // Dataset: function_result_MERRA2_100.statD_2d_slv_Nx.19800101.SUB.nc4
        // min, 0
        // max, 37800
        // is_monotonic, 0
        // there is no Vmax, what about Fill (NUG NetCDF USers Guide conventions for
        // missing data...valid min, max, range)
        // look for what the allowed interval means in SWE schema. Walk it into DAP.
        // In this case - use Attribute (not range function).

        List<Double> allowed1Interval = Arrays.asList(Double.valueOf("1.368420E-14"), Double.valueOf("7.8325e-14"));

        // good-grief...fix someday...works for now
        List<JAXBElement<List<Double>>> coordinates1 = new Vector<JAXBElement<List<Double>>>();
        coordinates1.add(sweFactory.createAllowedValuesTypeInterval(allowed1Interval));
        allowed1.setInterval(coordinates1);
        dataRecord1FieldQuantityAllowedValues.setAllowedValues(allowed1);
        dataRecord1FieldQuantity.setConstraint(dataRecord1FieldQuantityAllowedValues);

        dataRecord1Field.setAbstractDataComponent(sweFactory.createAbstractDataComponent(dataRecord1FieldQuantity));
        // dataRecord1Field.setAbstractDataComponent(new JAXBElement(new
        // QName("http://www.opengis.net/swe/2.0", "Quantity"),
        // dataRecord1FieldQuantity.getClass(),dataRecord1FieldQuantity));
        List<net.opengis.swecommon.v_2_0.DataRecordType.Field> dataRecord1FieldList = new ArrayList<net.opengis.swecommon.v_2_0.DataRecordType.Field>();
        dataRecord1FieldList.add(dataRecord1Field);
        dataRecord1.setField(dataRecord1FieldList);

        rangeType.setDataRecord(dataRecord1);

        // and so on for other dataRecords (#2 and #3)

        cd.setRangeType(rangeType);

        net.opengis.wcs.v_2_0.ServiceParametersType serviceParameters = new net.opengis.wcs.v_2_0.ServiceParametersType();
        net.opengis.wcs.v_2_0.ObjectFactory wcsFactory = new net.opengis.wcs.v_2_0.ObjectFactory();
        // Loop over all variables, ask two questions of rightmost two dimensions and
        // the size of those left of the rightmost two dimensions
        // and if some criteria are true that particular variable can be a field in a
        // coverage
        // Default: Rectified Grid - modulo one thing - lat long dimensions be monotonic
        // and be evenly spaced...
        // Lets punt...??..could be added to the Range Function...to tell us whether it
        // is a rectified grid.
        // There is cheesy way:compute the differences and say are they the same or not
        serviceParameters
                .setCoverageSubtype(new QName("http://www.opengis.net/wcs/2.0", "RectifiedGridCoverage", "wcs"));
        serviceParameters.setNativeFormat("application/vnd.opendap.dap4.data");

        cd.setServiceParameters(serviceParameters);

        //////////////////////////////
        // OK - push this on stack (edge cases)
        // difference between field and coverages
        // without field - coverage meaningless
        // Data set could have Multiple arrays - not same coverage
        // All MERRA - single coverage inside individual dataset
        // Not Testbed-13 anymore
        // Need then to characterize them with shared dimensions

        //
        // if have multiple fields.
        // Simpler set of conditions. Ignore cases where there are multiple shared dimensions or even no shared dimensions. DAP dataset could be a field in a coverage
        // but since they have different envelopes, different (separate coverages)
        // For now - assume same envelope.  But look for and detect when fields may not be and in those cases
        // say we will not support this dataset right now.

        // Make a list to come back to later - Punt on
        // Multiple fields can be automated - not multiple coverages.
        // Datasets that are not WGS 84
        //
        // Need to have a loop written to generate different coverage descriptions for different DMRs

        // first set coverageID = get from DMR name attribute of root element
        // for every variable in DMR.

        // Boiler plate JAXB marshaling of Coverage Description object into JDOM

        ////////////////////////////////////////////////////////
        // Since this was generated from third-party XML schema
        // need to bootstrap the JAXBContext
        // from the package name of the generated model
        // or the ObjectFactory class
        // (i.e. just have to know the package: net.opengis.wcs.v_2_0)

        // Required: First, bootstrap context with known WCS package name

        Marshaller jaxbMarshaller;

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("net.opengis.wcs.v_2_0");
            jaxbMarshaller = jaxbContext.createMarshaller();
        } catch (JAXBException e) {
            String msg = "Failed to get JAXB Marshaller! JAXBException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }

        try {

            // optional:  output "pretty printed"
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // optional: this is a list of the schema definitions.
            jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                    "http://www.opengis.net/wcs/2.0 http://schemas.opengis.net/wcs/2.0/wcsAll.xsd " +
                            "http://www.opengis.net/gml/3.2 http://schemas.opengis.net/gml/3.2.1/gml.xsd " +
                            "http://www.opengis.net/gmlcov/1.0 http://schemas.opengis.net/gmlcov/1.0/gmlcovAll.xsd " +
                            "http://www.opengis.net/swe/2.0 http://schemas.opengis.net/sweCommon/2.0/swe.xsd");

            // optional:  capture namespaces per MyMapper, instead of ns2, ns8 etc
            //jaxbMarshaller.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper", new MyNamespaceMapper());
            jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new MyNamespaceMapper());

        } catch (PropertyException e) {
            _log.warn("NON-FATAL ISSUE WARNING: Another JAXB impl (not the reference implementation) is being used" +
                    "...namespace prefixes like wcs, gml will not show up...instead you will ns2, ns8 etc. Message" + e.getMessage());
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // per https://stackoverflow.com/questions/819720/no-xmlrootelement-generated-by-jaxb
        // method#1:  need to wrap CoverageDescription as JAXB element
        // marshal coverage description into console (more specifically, System.out)
        //jaxbMarshaller.marshal(new JAXBElement(new QName("http://www.opengis.net/wcs/2.0", "wcs"), CoverageDescriptionType.class, cd), System.out);

        // TODO: marshal this into the OLFS JDOM object representation of CoverageDescription...more directly

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            String msg = "Failed to get DocumentBuilder! ParserConfigurationException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }
        Document doc = db.newDocument();

        //////////////////////////////////////////////////////////////////////////////////////////
        // per https://stackoverflow.com/questions/819720/no-xmlrootelement-generated-by-jaxb/
        // method#2: wrap WCS Coverage Description as JAXB Element using Object Factory
        // marshal coverage description into a org.w3c.dom.Document...first

        // ... and then convert the resultant org.w3c.dom.Document to JDOM (1.1.3) ..which is what OLFS runs on
        // (for JDOM 2, the Builder would be org.jdom2.input.DOMBuilder)
        net.opengis.wcs.v_2_0.ObjectFactory wcsObjFactory = new net.opengis.wcs.v_2_0.ObjectFactory();
        try {
            jaxbMarshaller.marshal(wcsObjFactory.createCoverageDescription(cd), doc);
        } catch (JAXBException e) {
            String msg = "Failed to get marshall COverageDescription! JAXBException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }
        org.jdom.input.DOMBuilder jdb = new org.jdom.input.DOMBuilder();
        org.jdom.Document jdoc = jdb.build(doc);

        // gotcha!  This is what integrates into OLFS (mostly).
        // The rest of CoverageDescription object can be derive from whatever has been captured so far
        // or from _myCD (TODO).

        _myCD = jdoc.getRootElement();

        // couple of quick sanity checks
        _log.debug(_myCD.toString());
        Element coverageId = _myCD.getChild("CoverageId", WCS.WCS_NS);
        _log.debug(coverageId.getText());

    }

    /**
     * We examine a DAP variable and determine if we can produce
     * a Field from it?
     *
     * For now it just dumps the variables Dim and Attribute content.
     * I created this method simply coalesce a bunch of repetitive code into
     * a single place.
     * 
     * @param v
     */
    private void ingestDapVar(Variable v) {
        // list all dims of this Float32
        for (Dim dim : v.getDims()) {
            _log.debug(dim.toString());
        }
        for (Attribute attr : v.getAttributes()) {
            _log.debug(attr.toString());
        }

    }

    private Field getFieldInstance(opendap.dap4.Variable var) throws WcsException {

        String name = var.getName();
        List<opendap.dap4.Attribute> attributes = var.getAttributes();
        Hashtable<String, opendap.dap4.Attribute> attributesHash = new Hashtable();
        Iterator<opendap.dap4.Attribute> iter = attributes.iterator();
        while (iter.hasNext()) {
            opendap.dap4.Attribute attribute = iter.next();
            attributesHash.put(attribute.getName(), attribute);
        }

        net.opengis.swecommon.v_2_0.ObjectFactory sweFactory = new net.opengis.swecommon.v_2_0.ObjectFactory();
        net.opengis.swecommon.v_2_0.DataRecordType.Field dataRecord1Field = new net.opengis.swecommon.v_2_0.DataRecordType.Field();
        net.opengis.swecommon.v_2_0.QuantityType dataRecord1FieldQuantity = new net.opengis.swecommon.v_2_0.QuantityType();

        dataRecord1FieldQuantity.setDefinition("urn:ogc:def:dataType:OGC:1.1:measure");
        dataRecord1FieldQuantity.setDescription(attributesHash.get("long_name").getValue());
        // dataRecord1FieldQuantity.setId(var.getName());

        net.opengis.swecommon.v_2_0.UnitReference dataRecord1FieldQuantityUom = new net.opengis.swecommon.v_2_0.UnitReference();
        dataRecord1FieldQuantityUom.setCode(attributesHash.get("units").getValue());
        dataRecord1FieldQuantity.setUom(dataRecord1FieldQuantityUom);

        net.opengis.swecommon.v_2_0.AllowedValuesPropertyType dataRecord1FieldQuantityAllowedValues = new net.opengis.swecommon.v_2_0.AllowedValuesPropertyType();
        net.opengis.swecommon.v_2_0.AllowedValuesType allowed1 = new net.opengis.swecommon.v_2_0.AllowedValuesType();

        List<Double> allowed1Interval = Arrays.asList(Double.valueOf(attributesHash.get("vmin").getValue()),
                Double.valueOf(attributesHash.get("vmax").getValue()));

        // TODO good-grief...fix someday...works for now
        List<JAXBElement<List<Double>>> coordinates1 = new Vector<JAXBElement<List<Double>>>();
        coordinates1.add(sweFactory.createAllowedValuesTypeInterval(allowed1Interval));
        allowed1.setInterval(coordinates1);
        dataRecord1FieldQuantityAllowedValues.setAllowedValues(allowed1);
        dataRecord1FieldQuantity.setConstraint(dataRecord1FieldQuantityAllowedValues);

        dataRecord1Field.setAbstractDataComponent(sweFactory.createAbstractDataComponent(dataRecord1FieldQuantity));
        Field field;
        try {

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            JAXBContext jaxbContext = JAXBContext.newInstance("net.opengis.swecommon.v_2_0");
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.marshal(dataRecord1Field, doc);
            org.jdom.input.DOMBuilder jdb = new org.jdom.input.DOMBuilder();
            org.jdom.Document jdoc = jdb.build(doc);

            field = new Field(jdoc.getRootElement());

            // couple of quick sanity checks

            // FIXME Make this use the logging system's DEBUG setting so we can switch it off
            // or off at run-time. jhrg 9/6/17
            _log.debug(jdoc.getRootElement().toString());

        } catch (JAXBException |
                WcsException |
                ParserConfigurationException e) {

            StringBuilder sb = new StringBuilder();
            sb.append("Unable to build Field instance.");
            sb.append(" Caught ").append(e.getClass().getName());
            sb.append(" Message  ").append(e.getMessage());
            _log.error(sb.toString());
            throw new WcsException(sb.toString(), WcsException.NO_APPLICABLE_CODE);
        }
        return field;
    }

    // Sanity Check
    public static void main(String[] args) {
        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
        String testDmrUrl = "https://goldsmr4.gesdisc.eosdis.nasa.gov/opendap/MERRA2/M2I1NXASM.5.12.4/1992/01/MERRA2_200.inst1_2d_asm_Nx.19920123.nc4.dmr.xml";

        testDmrUrl = "http://test.opendap.org/opendap/testbed-13/MERRA2_100.tavgM_2d_int_Nx.198001.nc4.dmr.xml";
        try {
            ThreddsCatalogUtil tcc = new ThreddsCatalogUtil();
            org.jdom.Document dmrDoc = tcc.getDocument(testDmrUrl);
            Element dmrElement = dmrDoc.getRootElement();
            dmrElement.detach();
            CoverageDescription cd = new DynamicCoverageDescription(dmrElement);

            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            System.out.println("RESULT: " + cd.toString());
            xmlo.output(cd.getCoverageDescriptionElement(), System.out);
            System.out.println("");
            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            xmlo.output(cd.getCoverageSummary(), System.out);
            System.out.println("");
            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

}
