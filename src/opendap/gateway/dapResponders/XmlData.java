package opendap.gateway.dapResponders;

import opendap.bes.BESError;
import opendap.bes.BesXmlAPI;
import opendap.bes.Version;
import opendap.coreServlet.ReqInfo;
import opendap.gateway.BesGatewayApi;
import opendap.gateway.HttpResponder;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: 1/31/11
 * Time: 4:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlData extends HttpResponder {
    private Logger log;


    private static String defaultRegex = ".*\\.xdods";


    public XmlData(String sysPath) {
        super(sysPath, null, defaultRegex);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());

    }

    public XmlData(String sysPath, String pathPrefix) {
        super(sysPath, pathPrefix, defaultRegex);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());

    }

    public void respondToHttpRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {




        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);
        String xmlBase = request.getRequestURL().toString();


        String dataSourceUrl = BesGatewayApi.getDataSourceUrl(request, getPathPrefix());


        log.debug("respondToHttpRequest(): Sending XML Data response For: " + dataSource +
                    "    CE: '" + constraintExpression + "'");


        response.setContentType("text/xml");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dap_xml");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();



        Document reqDoc = BesGatewayApi.getRequestDocument(
                                                        BesGatewayApi.XML_DATA,
                                                        dataSourceUrl,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        xmlBase,
                                                        null,
                                                        null,
                                                        BesGatewayApi.XML_ERRORS);


        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());

        log.debug("BesGatewayApi.getRequestDocument() returned:\n "+xmlo.outputString(reqDoc));

        if(!BesGatewayApi.besTransaction(dataSource,reqDoc,os,erros)){
            String msg = new String(erros.toByteArray());
            log.error("sendDDX() encountered a BESError: "+msg);
            os.write(msg.getBytes());

        }



        os.flush();
        log.info("Sent XML Data response.");


    }
    private void sendSomeStuff(HttpServletResponse response) throws Exception {

        response.setContentType("text/html");

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream()));
        XMLOutputter xmlo = new XMLOutputter();


        pw.println("<h2>DAP XML Data  Response</h2>");
        pw.println("<p>This request is being handled by: "+getClass().getName()+"</p>");

        pw.flush();


    }


    public void sendXmlData(HttpServletRequest request,
                          HttpServletResponse response)
            throws Exception {


        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        response.setContentType("text/xml");
        Version.setOpendapMimeHeaders(request, response);
        response.setHeader("Content-Description", "dap_xml");

        response.setStatus(HttpServletResponse.SC_OK);

        String xdap_accept = request.getHeader("XDAP-Accept");


        log.debug("sendXmlData(): Data For: " + dataSource +
                    "    CE: '" + constraintExpression + "'");



        ServletOutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();


        if(!BesXmlAPI.writeXmlDataResponse(
                dataSource,
                constraintExpression,
                xdap_accept,
                os,
                erros)){

//            String msg = new String(erros.toByteArray());
//            log.error(msg);
//            os.write(msg.getBytes());

            response.setHeader("Content-Description", "dods_error");
            BESError besError = new BESError(new ByteArrayInputStream(erros.toByteArray()));
            //besError.setErrorCode(BESError.INTERNAL_ERROR);
            besError.sendErrorResponse(_systemPath,response);
            log.error(besError.getMessage());
        }

        os.flush();
        log.info("Sent XML Data Response.");




    }



}
