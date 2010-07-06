/////////////////////////////////////////////////////////////////////////////
// This file is part of the "OPeNDAP 4 Data Server (aka Hyrax)" project.
//
//
// Copyright (c) 2010 OPeNDAP, Inc.
// Author: Nathan David Potter  <ndp@opendap.org>
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
/////////////////////////////////////////////////////////////////////////////
package opendap.wcs.gatewayClient;

import opendap.coreServlet.*;
import opendap.bes.Version;
import opendap.bes.BESError;
import opendap.bes.BesXmlAPI;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.jdom.transform.XSLTransformer;
import org.slf4j.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;


/**
 * User: ndp
 * Date: Mar 12, 2008
 * Time: 3:39:14 PM
 */
public class WcsDispatchHandler implements DispatchHandler {
    private Logger log;
    private boolean initialized;
    private HttpServlet dispatchServlet;
    private String prefix = "/wcs";

    public WcsDispatchHandler() {

        super();

        log = org.slf4j.LoggerFactory.getLogger(getClass());
        initialized = false;

    }


    public void sendWCSResponse(HttpServletRequest request,
                                HttpServletResponse response) throws Exception {


        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String requestSuffix = ReqInfo.getRequestSuffix(request);
        boolean isContentsRequest = false;

        log.debug("dataSource=" + dataSource);

        String s = dataSource;

        if (dataSource.startsWith("/"))
            s = dataSource.substring(1, dataSource.length());

        if (s.endsWith("contents") && requestSuffix.equals("html")) {
            s = s.substring(0, s.lastIndexOf("contents"));
            isContentsRequest = true;
        }


        String[] path = s.split("/", 0);

        String msg = "path[" + path.length + "]:\n";

        for (int i = 0; i < path.length; i++)
            msg += "    path[" + i + "]: " + path[i] + "\n";
        log.debug(msg);


        String projectName, siteName, serviceName, coverageName, dateName;


        if (path.length >= 6) {
            projectName = path[1];
            siteName = path[2];
            serviceName = path[3];
            coverageName = path[4];
            dateName = path[5];

            // By adding back the rest of the bits we can support date range
            // subsampling because it uses "/" to seperate the bits.
            for (int i = 6; i < path.length; i++)
                dateName += "/" + path[i];

            log.debug("Requested DAP DATA!." +
                    "  projectName=" + projectName +
                    "  siteName: " + siteName +
                    "  serviceName: " + serviceName +
                    "  coverageName: " + coverageName +
                    "  dateName: " + dateName +
                    "  dataSource: " + dataSource);

            sendDAPResponse(request,
                    response,
                    projectName,
                    siteName,
                    serviceName,
                    coverageName,
                    dateName);
        } else {

            if (!dataSource.endsWith("/") && !isContentsRequest) {
                // Now that we certain that this is a directory request we
                // redirect the URL without a trailing slash to the one with.
                // This keeps everything copacetic downstream when it's time
                // to build the directory document.
                response.sendRedirect(Scrub.urlContent(request.getContextPath() + dataSource + "/"));
            }

            switch (path.length) {

                case 0:
                    log.error("This line should never be executed.");
                    break;

                case 1:
                    log.debug("Requested Projects page. dataSource=" + dataSource);
                    sendProjectsPage(request, response);
                    break;


                case 2:
                    projectName = path[1];

                    log.debug("Sending Sites page. " +
                            "projectName=" + projectName +
                            "  dataSource=" + dataSource);
                    sendSitesPage(request,
                            response,
                            projectName);

                    break;

                case 3:
                    projectName = path[1];
                    siteName = path[2];

                    log.debug("Sending WCSServers page." +
                            "  projectName=" + projectName +
                            "  siteName: " + siteName +
                            "  dataSource=" + dataSource);

                    sendServersPage(request,
                            response,
                            projectName,
                            siteName);

                    break;

                case 4:
                    projectName = path[1];
                    siteName = path[2];
                    serviceName = path[3];

                    log.debug("Sending CoverageOfferings page." +
                            "  projectName=" + projectName +
                            "  siteName: " + siteName +
                            "  serviceName: " + serviceName +
                            "  dataSource=" + dataSource);

                    sendCoverageOfferingsList(request,
                            response,
                            projectName,
                            siteName,
                            serviceName);
                    break;

                case 5:
                    projectName = path[1];
                    siteName = path[2];
                    serviceName = path[3];
                    coverageName = path[4];

                    log.debug("Sending Coverage page." +
                            "  projectName=" + projectName +
                            "  siteName: " + siteName +
                            "  serviceName: " + serviceName +
                            "  coverageName: " + coverageName +
                            "  dataSource=" + dataSource);

                    sendCoveragePage(request,
                            response,
                            projectName,
                            siteName,
                            serviceName,
                            coverageName);

                    break;


                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    log.info("Sent BAD URL.");

                    break;
            }
        }

    }


    public void init(HttpServlet servlet, Element config) throws Exception {
        if (initialized) return;


        List children;
        dispatchServlet = servlet;


        Element e = config.getChild("prefix");
        if(e!=null)
            prefix = e.getTextTrim();


        //Get config file name from config Element
        children = config.getChildren("File");

        if (children.isEmpty()) {
            throw new Exception("Bad Configuration. The <Handler> " +
                    "element that declares " + this.getClass().getName() +
                    " MUST provide 1 or more <File>  " +
                    "child elements.");
        } else {
            log.debug("processing WCS configuration file(s)...");

            String contentPath = ServletUtil.getContentPath(servlet);
            Iterator i = children.iterator();
            Element fileElem;
            String filename;

            while (i.hasNext()) {
                fileElem = (Element) i.next();
                filename = contentPath + fileElem.getTextTrim();

                log.debug("configuration file: " + filename);

                WcsManager.init(filename);

                log.debug("configuration file: " + filename+" processing complete.");
            }


        }

        // Read Config and establish Config state.


        log.info("Initialized.");
        initialized = true;
    }

    public boolean requestCanBeHandled(HttpServletRequest request) throws Exception {
        return wcsRequestDispatch(request, null, false);
    }

    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        wcsRequestDispatch(request, response, true);
    }


    private boolean wcsRequestDispatch(HttpServletRequest request,
                                       HttpServletResponse response,
                                       boolean sendResponse)
            throws Exception {

        String relativeURL = ReqInfo.getRelativeUrl(request);

        if(relativeURL.startsWith("/"))
            relativeURL = relativeURL.substring(1,relativeURL.length());


        boolean wcsRequest = false;

        if (relativeURL != null) {

            if (relativeURL.startsWith(prefix)) {
                wcsRequest = true;
                if (sendResponse) {
                    sendWCSResponse(request, response);
                    log.info("Sent WCS Response");
                }
            }
        }

        return wcsRequest;

    }

    public long getLastModified(HttpServletRequest req) {
        return -1;
    }

    public void destroy() {

        WcsManager.destroy();
        log.info("Destroy Complete");


    }


    private Element newDataset(String name,
                               boolean isData,
                               boolean thredds_collection,
                               long size,
                               Date date) {

        Element e;
        SimpleDateFormat sdf;
        String s;


        Element dataset = new Element("dataset");
        dataset.setAttribute("isData", isData + "");
        dataset.setAttribute("thredds_collection", thredds_collection + "");

        e = new Element("name");
        e.setText(name);
        dataset.addContent(e);

        e = new Element("size");
        e.setText(size + "");
        dataset.addContent(e);

        Element lastModified = new Element("lastmodified");

        e = new Element("date");
        sdf = new SimpleDateFormat("yyyy-MM-dd");
        s = sdf.format(date);
        e.setText(s);
        lastModified.addContent(e);

        e = new Element("time");
        sdf = new SimpleDateFormat("HH:mm:ss z");
        s = sdf.format(date);
        e.setText(s);
        lastModified.addContent(e);


        dataset.addContent(lastModified);


        return dataset;

    }


    public void sendProjectsPage(HttpServletRequest req,
                                 HttpServletResponse resp)
            throws Exception {

        Element p;
        long size = 0;


        String collectionName = Scrub.urlContent(ReqInfo.getRelativeUrl(req));

        if (collectionName.endsWith("/contents.html")) {
            collectionName = collectionName.substring(0, collectionName.lastIndexOf("contents.html"));
        }

        if (!collectionName.endsWith("/"))
            collectionName += "/";

        log.debug("collectionName:  " + collectionName);

        Element showCatalog = new Element("showCatalog");
        Element responseElem = new Element("response");

        Element topDataset = newDataset(collectionName, false, true, size, new Date());

        showCatalog.addContent(responseElem);
        responseElem.addContent(topDataset);



        topDataset.setAttribute("prefix", "/");


        Collection<Project> projects = WcsManager.getProjects();

        for (Project proj : projects) {
            //size = proj.getSize();
            p = newDataset(proj.getName(), false, true, 0, new Date());
            topDataset.addContent(p);
        }

        Document catalog = new Document(showCatalog);

        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());


        String xsltDoc = ServletUtil.getSystemPath(dispatchServlet, "/docs/xsl/contents.xsl");

        XSLTransformer transformer = new XSLTransformer(xsltDoc);

        Document contentsPage = transformer.transform(catalog);


        //xmlo.output(catalog, System.out);
        //xmlo.output(contentsPage, System.out);

        resp.setContentType("text/html");
        resp.setHeader("Content-Description", "dods_directory");
        resp.setStatus(HttpServletResponse.SC_OK);
        xmlo.output(contentsPage, resp.getWriter());

    }


    public void sendSitesPage(HttpServletRequest request,
                              HttpServletResponse response,
                              String projectName)
            throws Exception {


        String collectionName = Scrub.urlContent(ReqInfo.getRelativeUrl(request));

        if (collectionName.endsWith("/contents.html")) {
            collectionName = collectionName.substring(0, collectionName.lastIndexOf("contents.html"));
        }

        if (!collectionName.endsWith("/"))
            collectionName += "/";

        log.debug("collectionName:  " + collectionName);

        Document catalog = new Document();
        Element s;
        long size = 0;

        Element showCatalog = new Element("showCatalog");
        Element responseElem = new Element("response");

        Element topDataset = newDataset(collectionName, false, true, size, new Date());

        showCatalog.addContent(responseElem);
        responseElem.addContent(topDataset);

        topDataset.setAttribute("prefix", "/");

        catalog.setRootElement(showCatalog);

        Project project = WcsManager.getProject(projectName);
        if (project == null) {
            log.error("sendSitesPage() Project:  \"" + projectName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }


        Vector<Site> sites = project.getSites();

        for (Site site : sites) {
            //size = WcsManager.getWcsServiceCount();
            s = newDataset(site.getName(), false, true, 0, new Date());
            topDataset.addContent(s);
        }

        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());

        String xsltDoc = ServletUtil.getSystemPath(dispatchServlet, "/docs/xsl/contents.xsl");

        XSLTransformer transformer = new XSLTransformer(xsltDoc);

        Document contentsPage = transformer.transform(catalog);


        //xmlo.output(catalog, System.out);
        //xmlo.output(contentsPage, System.out);

        response.setContentType("text/html");
        response.setHeader("Content-Description", "dods_directory");
        response.setStatus(HttpServletResponse.SC_OK);
        xmlo.output(contentsPage, response.getWriter());

    }


    public void sendServersPage(HttpServletRequest request,
                                HttpServletResponse response,
                                String projectName,
                                String siteName)
            throws Exception {
        String collectionName = Scrub.urlContent(ReqInfo.getRelativeUrl(request));

        if (collectionName.endsWith("/contents.html")) {
            collectionName = collectionName.substring(0, collectionName.lastIndexOf("contents.html"));
        }

        if (!collectionName.endsWith("/"))
            collectionName += "/";

        log.debug("collectionName:  " + collectionName);

        Document catalog = new Document();
        Element s;
        long size = 0;


        Element showCatalog = new Element("showCatalog");
        Element responseElem = new Element("response");

        Element topDataset = newDataset(collectionName, false, true, size, new Date());

        showCatalog.addContent(responseElem);
        responseElem.addContent(topDataset);
        topDataset.setAttribute("prefix", "/");

        catalog.setRootElement(showCatalog);

        Project project = WcsManager.getProject(projectName);
        if (project == null) {
            log.error("sendServersPage() Project:  \"" + projectName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Site site = project.getSite(siteName);
        if (site == null) {
            log.error("sendServersPage() Site:  \"" + siteName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Collection<WcsService> services = WcsManager.getWcsServices();

        for (WcsService service : services) {
            //size = service.getCoverageCount();
            s = newDataset(service.getName(), false, true, 0, new Date());
            topDataset.addContent(s);
        }

        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());

        String xsltDoc = ServletUtil.getSystemPath(dispatchServlet, "/docs/xsl/contents.xsl");

        XSLTransformer transformer = new XSLTransformer(xsltDoc);

        Document contentsPage = transformer.transform(catalog);


        //xmlo.output(catalog, System.out);
        //xmlo.output(contentsPage, System.out);

        response.setContentType("text/html");
        response.setHeader("Content-Description", "dods_directory");
        response.setStatus(HttpServletResponse.SC_OK);
        xmlo.output(contentsPage, response.getWriter());

    }

    public void sendCoverageOfferingsList(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String projectName,
                                 String siteName,
                                 String serviceName)
            throws Exception {

        String collectionName = Scrub.urlContent(ReqInfo.getRelativeUrl(request));

        if (collectionName.endsWith("/contents.html")) {
            collectionName = collectionName.substring(0, collectionName.lastIndexOf("contents.html"));
        }

        if (!collectionName.endsWith("/"))
            collectionName += "/";

        log.debug("collectionName:  " + collectionName);


       /*

        Element root = newDataset(collectionName, false, true, size, new Date());
        root.setAttribute("prefix", "/");

        catalog.setRootElement(root);

       */

        Project project = WcsManager.getProject(projectName);
        if (project == null) {
            log.error("sendCoverageOfferingsList() Project:  \"" + projectName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Site site = project.getSite(siteName);
        if (site == null) {
            log.error("sendCoverageOfferingsList() Site:  \"" + siteName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        WcsService service = WcsManager.getWcsService(serviceName);
        if (service == null) {
            log.error("sendCoverageOfferingsList() WcsService:  \"" + serviceName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }



        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
        String xsltDoc = ServletUtil.getSystemPath(dispatchServlet, "/docs/xsl/wcs_coveragesList.xsl");
        XSLTransformer transformer = new XSLTransformer(xsltDoc);

        ReentrantReadWriteLock.ReadLock lock = service.getReadLock();
        try {
            lock.lock();
            Document capDoc = service.getCapabilitiesDocument();
            //xmlo.output(capDoc, System.out);

            Document contentsPage = transformer.transform(capDoc);

            //xmlo.output(contentsPage, System.out);

            response.setContentType("text/html");
            response.setHeader("Content-Description", "dods_directory");
            response.setStatus(HttpServletResponse.SC_OK);
            xmlo.output(contentsPage, response.getWriter());

        }
        finally {
            lock.unlock();
        }

    }


    public void sendCoveragePage(HttpServletRequest request,
                              HttpServletResponse response,
                              String projectName,
                              String siteName,
                              String serviceName,
                              String coverageName)
            throws Exception {

        /*
        String collectionName = Scrub.urlContent(ReqInfo.getRelativeUrl(request));

        if (collectionName.endsWith("/contents.html")) {
            collectionName = collectionName.substring(0, collectionName.lastIndexOf("contents.html"));
        }

        if (!collectionName.endsWith("/"))
            collectionName += "/";

        log.debug("collectionName:  " + collectionName);
*/
        Project project = WcsManager.getProject(projectName);
        if (project == null) {
            log.error("sendCoveragePage() Project:  \"" + projectName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Site site = project.getSite(siteName);
        if (site == null) {
            log.error("sendCoveragePage() Site:  \"" + siteName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        WcsService service = WcsManager.getWcsService(serviceName);
        if (service == null) {
            log.error("sendCoveragePage() WcsService:  \"" + serviceName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }


        ReentrantReadWriteLock.ReadLock lock = service.getReadLock();
        try {
            lock.lock();

            WcsCoverageOffering coverage = service.getCoverageOffering(coverageName);
            if (coverage == null) {
                log.error("sendCoveragePage() Coverage:  \"" + coverageName + "\" not found.");
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            else {
                log.debug("sendCoveragePage() Sending Coverage:  \"" + coverage.getName() + "\"");

                Element coff = coverage.getConfigElement();
                Element s;
                DateFormat df = new SimpleDateFormat("yyy-mm-dd");

                coff.detach();

                Document doc = new Document(coff);

                Document pageContent = null;
                XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
                String xsltDoc = ServletUtil.getSystemPath(dispatchServlet, "/docs/xsl/wcs_coveragePage.xsl");
                XSLTransformer transformer = new XSLTransformer(xsltDoc);

                if(coverage.hasTemporalDomain()){
                    log.debug("sendCoveragePage() " + coverage.getName() +
                            " has a temporal domain. Adding time datasets.");
                    Element dset = coff.getChild(WCS.DOMAIN_SET,WCS.NS);
                    Element tdom = dset.getChild(WCS.TEMPORAL_DOMAIN,WCS.NS);

                    Vector<String> dates = coverage.generateDateStrings();
                    for (String day : dates) {
                        s = newDataset(day, true, false, 0, df.parse(day));
                        tdom.addContent(s);
                    }

                    pageContent = transformer.transform(doc);

                }
                else if(coverage.hasSpatialDomain()){
                    log.debug("sendCoveragePage() " + coverage.getName() +
                            " has no temporal domain.");
                    pageContent = transformer.transform(doc);

                }


                //xmlo.output(coff, System.out);
                //xmlo.output(pageContent, System.out);

                response.setContentType("text/html");
                response.setHeader("Content-Description", "dods_directory");
                response.setStatus(HttpServletResponse.SC_OK);
                xmlo.output(pageContent, response.getWriter());



            }

        }
        finally {
            lock.unlock();
        }


    }


    public void sendDAPResponse(HttpServletRequest request,
                                HttpServletResponse response,
                                String projectName,
                                String siteName,
                                String serviceName,
                                String coverageName,
                                String dateName)
            throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String requestSuffix = ReqInfo.getRequestSuffix(request);


        Project project = WcsManager.getProject(projectName);
        if (project == null) {
            log.error("sendDAPResponse() Project:  \"" + projectName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Site site = project.getSite(siteName);
        if (site == null) {
            log.error("sendDAPResponse() Site:  \"" + siteName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        WcsService service = WcsManager.getWcsService(serviceName);
        if (service == null) {
            log.error("sendDAPResponse() WcsService:  \"" + serviceName + "\" not found.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String wcsRequestURL="";
        ReentrantReadWriteLock.ReadLock lock = service.getReadLock();
        try {
            lock.lock();
            WcsCoverageOffering coverage = service.getCoverageOffering(coverageName);
            if (coverage == null) {
                log.error("sendDAPResponse() Coverage:  \"" + serviceName + "\" not found.");
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if(dateName.equals("dataset"))
                dateName = null;

            wcsRequestURL = service.getWcsRequestURL(site,coverage,dateName);
            log.debug("wcsRequestURL: "+wcsRequestURL);
        }
        finally{
            lock.unlock();
        }


        if ( // DDS Response?
                requestSuffix.equalsIgnoreCase("dds")
                ) {

            sendDDS(request, response, wcsRequestURL);
            log.info("Sent DDS");


        } else if ( // DAS Response?
                requestSuffix.equalsIgnoreCase("das")
                ) {
            sendDAS(request, response, wcsRequestURL);
            log.info("Sent DAS");


        } else if (  // DDX Response?
                requestSuffix.equalsIgnoreCase("ddx")
                ) {
            sendDDX(request, response, wcsRequestURL);
            log.info("Sent DDX");


        } else if ( // DAP2 (aka .dods) Response?
                requestSuffix.equalsIgnoreCase("dods")
                ) {
            sendDAP2Data(request, response, wcsRequestURL);
            log.info("Sent DAP2 Data");


        } else if (  // ASCII Data Response.
                requestSuffix.equalsIgnoreCase("asc") ||
                        requestSuffix.equalsIgnoreCase("ascii")
                ) {
            sendASCII(request, response, wcsRequestURL);
            log.info("Sent ASCII");


        } else if (  // Info Response?
                requestSuffix.equalsIgnoreCase("info")
                ) {
            sendINFO(request, response, wcsRequestURL);
            log.info("Sent Info");


        } else if (  //HTML Request Form (aka The Interface From Hell) Response?
                requestSuffix.equalsIgnoreCase("html") ||
                        requestSuffix.equalsIgnoreCase("htm")
                ) {
            sendHTMLRequestForm(request, response, wcsRequestURL);
            log.info("Sent HTML Request Form");


        } else if (requestSuffix.equals("")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            log.info("Sent BAD URL (missing Suffix)");

        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            log.info("Sent BAD URL - not an OPeNDAP request suffix.");
        }

    }

    private void sendDDS(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        log.debug("sendDDS() for dataset: " + dataSource);

        response.setContentType("text/plain");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);


        String xdap_accept = request.getHeader("XDAP-Accept");



        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();


        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.DDS,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        null,
                                                        null,
                                                        null,
                                                        BesXmlAPI.DAP2_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){

            String msg = new String(erros.toByteArray());
            log.error("sendDDS() encounterd a BESError. Error Message:\n"+msg);
            os.write(msg.getBytes());
        }


        os.flush();



    }




    private void sendDAS(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        log.debug("sendDAS() for dataset: " + dataSource);

        response.setContentType("text/plain");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.DAS,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        null,
                                                        null,
                                                        null,
                                                        BesXmlAPI.DAP2_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){

            String msg = new String(erros.toByteArray());
            log.error("sendDAS() encounterd a BESError: "+msg);
            os.write(msg.getBytes());

        }


        os.flush();



    }



    private void sendDDX(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);
        String xmlBase = request.getRequestURL().toString();

        log.debug("sendDDX() for dataset: " + dataSource);

        response.setContentType("text/plain");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();


        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.DDX,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        xmlBase,
                                                        null,
                                                        null,
                                                        BesXmlAPI.DAP2_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){
            String msg = new String(erros.toByteArray());
            log.error("sendDDX() encounterd a BESError: "+msg);
            os.write(msg.getBytes());
        }


        os.flush();



    }



    private void sendDAP2Data(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        log.debug("sendDAP2Data() for dataset: " + dataSource);

        response.setContentType("text/plain");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.DAP2,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        null,
                                                        null,
                                                        null,
                                                        BesXmlAPI.DAP2_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){
            String msg = new String(erros.toByteArray());
            log.error("sendDAP2Data() encounterd a BESError: "+msg);
            os.write(msg.getBytes());

        }


        os.flush();



    }

    private void sendASCII(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        log.debug("sendASCII() for dataset: " + dataSource);

        response.setContentType("text/plain");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();


        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.ASCII,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        null,
                                                        null,
                                                        null,
                                                        BesXmlAPI.XML_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){

            BESError besError = new BESError(new ByteArrayInputStream(erros.toByteArray()));
            besError.sendErrorResponse(dispatchServlet,response);
            log.error("sendASCII() encounterd a BESError: "+besError.getMessage());
        }


        os.flush();


    }


    private void sendINFO(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);

        log.debug("sendINFO() for dataset: " + dataSource);

        response.setContentType("text/html");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_dds");
        // Commented because of a bug in the OPeNDAP C++ stuff...
        //response.setHeader("Content-Encoding", "plain");

        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        OutputStream os = response.getOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.INFO_PAGE,
                                                        wcsRequestURL,
                                                        constraintExpression,
                                                        xdap_accept,
                                                        null,
                                                        null,
                                                        null,
                                                        BesXmlAPI.XML_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){
            BESError besError = new BESError(new ByteArrayInputStream(erros.toByteArray()));
            besError.sendErrorResponse(dispatchServlet,response);
            log.error("sendINFO() encounterd a BESError: "+besError.getMessage());

        }


        os.flush();


    }


    private void sendHTMLRequestForm(HttpServletRequest request, HttpServletResponse response, String wcsRequestURL) throws Exception {

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
        String requestSuffix = ReqInfo.getRequestSuffix(request);

        log.debug("sendHTMLRequestForm() for dataset: " + dataSource);

        response.setContentType("text/html");
        Version.setOpendapMimeHeaders(request,response);
        response.setHeader("Content-Description", "dods_form");


        response.setStatus(HttpServletResponse.SC_OK);
        String xdap_accept = request.getHeader("XDAP-Accept");

        log.debug("sendHTMLRequestForm(): Sending HTML Data Request Form For: "
                + dataSource +
                "    CE: '" + request.getQueryString() + "'");


        OutputStream os = response.getOutputStream();

        String url = request.getRequestURL().toString();

        int suffix_start = url.lastIndexOf("." + requestSuffix);

        url = url.substring(0, suffix_start);


        log.debug("sendHTMLRequestForm(): HTML Form URL: " + url);

        ByteArrayOutputStream erros = new ByteArrayOutputStream();

        Document reqDoc = BesAPI.getRequestDocument(
                                                        BesXmlAPI.HTML_FORM,
                                                        wcsRequestURL,
                                                        null,
                                                        xdap_accept,
                                                        null,
                                                        url,
                                                        null,
                                                        BesXmlAPI.XML_ERRORS);

        if(!BesAPI.besTransaction(dataSource,reqDoc,os,erros)){
            BESError besError = new BESError(new ByteArrayInputStream(erros.toByteArray()));

            besError.sendErrorResponse(dispatchServlet,response);


            String msg = besError.getMessage();
            //System.out.println(msg);
            //System.err.println(msg);
            log.error("sendHTMLRequestForm() encounterd a BESError: "+msg);
        }

        os.flush();




    }






}


