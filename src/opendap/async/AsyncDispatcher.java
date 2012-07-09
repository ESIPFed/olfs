/////////////////////////////////////////////////////////////////////////////
// This file is part of the "OPeNDAP 4 Data Server (aka Hyrax)" project.
//
//
// Copyright (c) 2011 OPeNDAP, Inc.
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
package opendap.async;

import opendap.bes.BES;
import opendap.bes.BESConfig;
import opendap.bes.BESManager;
import opendap.bes.dapResponders.BesApi;
import opendap.bes.dapResponders.DapDispatcher;
import opendap.coreServlet.ReqInfo;
import opendap.namespaces.DAP;
import opendap.namespaces.DublinCore;
import opendap.namespaces.XLINK;
import opendap.namespaces.XML;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.ProcessingInstruction;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IsoDispatchHandler for ISO responses from Hyrax
 */
public class AsyncDispatcher extends DapDispatcher {



    private Logger log;
    private boolean initialized;

    private ConcurrentHashMap<String,Date> asyncCache;

    private String _prefix = "async/";

    private int cachePersistTime; // In milliseconds
    private int responseDelay; // In milliseconds


    public AsyncDispatcher(){
        log = LoggerFactory.getLogger(getClass());

        asyncCache = new ConcurrentHashMap<String, Date>();

        cachePersistTime = 3600000; // In milliseconds
        responseDelay    = 60000;   // In milliseconds

        initialized = false;
    }


    @Override
    public void init(HttpServlet servlet,Element config) throws Exception {


        if(initialized) return;

        BesApi besApi = new BesApi();

        init(servlet, config ,besApi);

        ingestPrefix();
        ingestCachePersistTime();
        ingestResponseDelay();


        Element besManagerConfig = BESManager.getConfig();
        List besList = besManagerConfig.getChildren("BES");

        BES bes;
        BESConfig besConfig;
        Element besConfigElement;
        for (Object o : besList) {
            besConfigElement = (Element) o;

            Element prefixElement = besConfigElement.getChild("prefix");
            String prefix = null;
            if(prefixElement!=null)
                prefix = prefixElement.getTextTrim();

            if(prefix!=null && prefix.equals("/")){

                prefixElement.setText(_prefix);

                besConfig = new BESConfig(besConfigElement);
                bes = new BES(besConfig);
                BESManager.addBes(bes);
                log.info("Added BES to service asynchronous responses. BES prefix: '"+_prefix+"'");

                initialized = true;
                return;
            }
        }


    }

    private void ingestPrefix() throws Exception{

        String msg;

        Element config = getConfig();

        Element e = config.getChild("prefix");
        if(e!=null)
            _prefix = e.getTextTrim();

        if(_prefix.equals("/")){
            msg = "Bad Configuration. The <Handler> " +
                    "element that declares " + this.getClass().getName() +
                    " MUST provide 1 <prefix>  " +
                    "child element whose value may not be equal to \"/\"";
            log.error(msg);
            throw new Exception(msg);
        }



        if(_prefix.endsWith("/"))
            _prefix = _prefix.substring(0,_prefix.length()-1);

        if(!_prefix.startsWith("/"))
            _prefix = "/" + _prefix;

        log.info("prefix="+ _prefix);

    }

    private void ingestCachePersistTime() throws Exception{

        String msg;

        Element config = getConfig();

        Element e = config.getChild("cachePersistTime");


        if(e!=null)
            cachePersistTime = Integer.parseInt(e.getTextTrim());

        if(cachePersistTime < 0){
            msg = "Bad Configuration. The <Handler> " +
                    "element that declares " + this.getClass().getName() +
                    " MUST provide a <cachePersistTime>  " +
                    "child element whose value may not be less than 0";
            log.error(msg);
            throw new Exception(msg);
        }
        log.info("cachePersistTime="+ cachePersistTime);

    }



    private void ingestResponseDelay() throws Exception{

        String msg;

        Element config = getConfig();

        Element e = config.getChild("responseDelay");


        if(e!=null)
            responseDelay = Integer.parseInt(e.getTextTrim());

        if(responseDelay < 0){
            msg = "Bad Configuration. The <Handler> " +
                    "element that declares " + this.getClass().getName() +
                    " MUST provide a <responseDelay>  " +
                    "child element whose value may not be less than 0";
            log.error(msg);
            throw new Exception(msg);
        }
        log.info("responseDelay="+ responseDelay);

    }




    @Override
    public boolean requestDispatch(HttpServletRequest request,
                                    HttpServletResponse response,
                                    boolean sendResponse)
            throws Exception {



        String serviceContext = ReqInfo.getFullServiceContext(request);
        String relativeURL = ReqInfo.getLocalUrl(request);


        log.debug("serviceContext: "+serviceContext);
        log.debug("relativeURL:    "+relativeURL);

        if(!relativeURL.startsWith("/"))
            relativeURL = "/" + relativeURL;


        boolean isMyRequest = relativeURL.startsWith(_prefix);



        if (isMyRequest) {
            if(sendResponse){
                return(sendAsyncResponse(request, response));
            }
            else {
                return(super.requestDispatch(request, response, false));
            }
        }

        return isMyRequest;






    }




    private String dap4DataRegex = ".*\\.dap|.*\\.xdods";
    private String dap4MetadataRegex = ".*\\.xml|.*\\.iso|.*\\.rubric|.*\\.ver|.*\\.ddx|.*\\.dds|.*\\.das|.*\\.info|.*\\.html?";
    private String dap2Regex = ".*\\.dods|.*\\.asc(ii)?";

    private String servicesRegex = dap4DataRegex + "|" + dap4MetadataRegex + "|" + dap2Regex;

    private Pattern dap4DataPattern = Pattern.compile(dap4DataRegex, Pattern.CASE_INSENSITIVE);
    private Pattern dap4MetadataPattern = Pattern.compile(dap4MetadataRegex, Pattern.CASE_INSENSITIVE);
    private Pattern dap2Pattern = Pattern.compile(dap2Regex, Pattern.CASE_INSENSITIVE);

    private Pattern servicesPattern = Pattern.compile(servicesRegex, Pattern.CASE_INSENSITIVE);



    public boolean sendAsyncResponse(HttpServletRequest request,
                              HttpServletResponse response) throws Exception {

        log.info("Sending Asynchronous Response");



        String relativeURL = ReqInfo.getLocalUrl(request);

        Matcher m;

        m = dap4DataPattern.matcher(relativeURL);
        if(m.matches()){
            return(asyncResponse(request, response, false));
        }

        m = dap2Pattern.matcher(relativeURL);
        if(m.matches()){
            return(asyncResponse(request, response, true));
        }

        return(super.requestDispatch(request,response,true));


    }








    public boolean asyncResponse(HttpServletRequest request, HttpServletResponse response, boolean isDap2Request) throws Exception {

        Date now = new Date();
        Date startTime = new Date(now.getTime()+getResponseDelay());
        Date endTime = new Date(startTime.getTime()+cachePersistTime);

        String xmlBase = getXmlBase(request);

        boolean cacheIsReady = false;

        if(asyncCache.containsKey(xmlBase)) {

            startTime = asyncCache.get(xmlBase);

            endTime = new Date(startTime.getTime()+cachePersistTime);



            if(now.after(startTime)){
                if(now.before(endTime) ){
                    cacheIsReady = true;
                }
                else if(now.after(endTime)){
                    asyncCache.remove(xmlBase);
                }
            }
        }


        if(!asyncCache.containsKey(xmlBase)) {
            startTime = new Date(now.getTime()+getResponseDelay());
            endTime = new Date(startTime.getTime()+cachePersistTime);
            asyncCache.put(xmlBase,startTime);
        }



        if(cacheIsReady){
            return(super.requestDispatch(request,response,true));
        }
        else {

            if(!isDap2Request){


                Document asyncResponse = getAsynchronousResponseDoc(request,startTime,endTime);
                XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());




                System.out.println(xmlo.outputString(asyncResponse));
                response.setContentType("text/xml");
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                response.getOutputStream().print(xmlo.outputString(asyncResponse));
            }
            else {
               long timeTillReady = startTime.getTime() - now.getTime();

               if(timeTillReady>0){
                   log.info("Delaying DAP2 data request for "+timeTillReady+"ms");
                   try { Thread.sleep(timeTillReady);}
                   catch(InterruptedException e){ log.error("Thread Interrupted. msg: "+e.getMessage());}
               }

               return(super.requestDispatch(request,response,true));
            }
        }

        return(true);


    }






    private int getResponseDelay(){


        return responseDelay;
    }




    public Document getAsynchronousResponseDoc(HttpServletRequest request, Date firstTimeAvailable, Date lastTimeAvailable){


        String context  = request.getContextPath()+"/";

        Element dataset = new Element("Dataset", DAP.DAPv40_NS);
        Element async   = new Element("async",DAP.DAPv40_NS);
        Element beginAccess  = new Element("beginAccess", DAP.DAPv40_NS);
        Element endAccess    = new Element("endAccess", DAP.DAPv40_NS);

        async.addContent(beginAccess);
        async.addContent(endAccess);
        dataset.addContent(async);
        dataset.addNamespaceDeclaration(DublinCore.NS);
        dataset.addNamespaceDeclaration(XLINK.NS);

        String xmlBase =  getXmlBase(request);
        String requestUrl = request.getRequestURL().toString();
        String ce = request.getQueryString();

        dataset.setAttribute("base",xmlBase, XML.NS);
        async.setAttribute("href",requestUrl+"?"+ce, XLINK.NS);


        log.debug("firstTime: "+firstTimeAvailable.getTime());
        log.debug("lastTime:  "+lastTimeAvailable.getTime());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

        StringBuffer startTime = new StringBuffer();

        startTime = sdf.format(firstTimeAvailable,startTime, new FieldPosition(DateFormat.YEAR_FIELD));

        StringBuffer endTime = new StringBuffer();

        endTime = sdf.format(lastTimeAvailable,endTime,new FieldPosition(DateFormat.YEAR_FIELD));

        beginAccess.setText(startTime.toString());
        endAccess.setText(endTime.toString());

        HashMap<String,String> piMap = new HashMap<String,String>( 2 );
        piMap.put( "type", "text/xsl" );
        piMap.put( "href", context+"xsl/asyncResponse.xsl" );
        ProcessingInstruction pi = new ProcessingInstruction( "xml-stylesheet", piMap );

        Document asyncResponse = new Document() ;
        asyncResponse.addContent( pi );

        asyncResponse.setRootElement(dataset);


        return asyncResponse;

    }



    public String getXmlBase(HttpServletRequest req){

        String forwardRequestUri = (String)req.getAttribute("javax.servlet.forward.request_uri");
        String requestUrl = req.getRequestURL().toString();


        if(forwardRequestUri != null){
            String server = req.getServerName();
            int port = req.getServerPort();
            String scheme = req.getScheme();
            requestUrl = scheme + "://" + server + ":" + port + forwardRequestUri;
        }


        String xmlBase = removeRequestSuffixFromString(requestUrl);


        log.debug("@xml:base='{}'",xmlBase);
        return xmlBase;
    }

    public String removeRequestSuffixFromString(String requestString){
        String trimmedRequestString;

        trimmedRequestString = requestString.substring(0,requestString.lastIndexOf("."));

        return trimmedRequestString;
    }







}