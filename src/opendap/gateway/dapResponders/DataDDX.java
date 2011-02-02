package opendap.gateway.dapResponders;

import opendap.bes.BesXmlAPI;
import opendap.bes.Version;
import opendap.coreServlet.MimeBoundary;
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
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;


public class DataDDX extends HttpResponder {



    private Logger log;

    private static String defaultRegex = ".*\\.dap";


    public DataDDX(String sysPath) {
        super(sysPath, null, defaultRegex);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());

    }

    public DataDDX(String sysPath, String pathPrefix) {
        super(sysPath, pathPrefix, defaultRegex);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());

    }

    public void respondToHttpRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {


        String xmlBase = request.getRequestURL().toString();
        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);
        String dataSourceUrl = BesGatewayApi.getDataSourceUrl(request, getPathPrefix());




        MimeBoundary mb = new MimeBoundary();
        String startID = mb.newContentID();

        log.debug("sendDataDDX() for dataset: " + dataSource+
                "    CE: '" + constraintExpression + "'");


        response.setContentType("Multipart/Related;  "+
                                "type=\"text/xml\";  "+
                                "start=\"<"+startID+">\";  "+
                                "boundary=\""+mb.getBoundary()+"\"");


        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dap4_data_ddx");

        // This header indicates to the client that the content of this response
        // is dependant on the HTTP request header XDAP-Accept
        response.setHeader("Vary", "XDAP-Accept");

        // Because the content of this response is dependant on a client provided
        // HTTP header (XDAP-Accept) it is useful to include this Cach-Control
        // header to make caching work correctly...
        response.setHeader("Cache-Control", "public");


        response.setStatus(HttpServletResponse.SC_OK);

        String xdap_accept = request.getHeader("XDAP-Accept");





        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = BesGatewayApi.getDataDDXRequest(dataSourceUrl,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        xmlBase,
                                                        startID,
                                                        mb.getBoundary());

        if(!BesGatewayApi.besTransaction(dataSource,reqDoc,os,erros)){
            String msg = new String(erros.toByteArray());
            log.error("sendDAP2Data() encountered a BESError: "+msg);
            os.write(msg.getBytes());

        }


        os.flush();
        log.info("Sent DAP4 Data DDX response.");




    }

}
