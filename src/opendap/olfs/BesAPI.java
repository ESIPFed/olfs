/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Server4" project, a Java implementation of the
// OPeNDAP Data Access Protocol.
//
// Copyright (c) 2005 OPeNDAP, Inc.
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


package opendap.olfs;

import opendap.util.Debug;
import opendap.ppt.OPeNDAPClient;
import opendap.ppt.PPTException;

import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: Aug 18, 2005
 * Time: 10:40:00 AM
 * To change this template use File | Settings | File Templates.
 */
public class BesAPI {

    public static void getDDS(ReqState rs,
                              OutputStream os)
            throws BadConfigurationException, PPTException {

        besGetTransaction("dds",rs,os);
    }


    public static void getDAS(ReqState rs,
                              OutputStream os)
            throws BadConfigurationException, PPTException {

        besGetTransaction("das",rs,os);
    }

    public static void getDODS(ReqState rs,
                               OutputStream os)
            throws BadConfigurationException, PPTException {

        besGetTransaction("dods",rs,os);
    }

/*
    public static InputStream getDODSStream(ReqState rs)
            throws BadConfigurationException, PPTException {

        besGetStream("dods",rs,os);
    }

    public static void closeDODSStream(ReqState rs)
            throws BadConfigurationException, PPTException {

        besCloseStream("dods",rs,os);
    }
*/



    public static void showVersion(ReqState rs,
                                   OutputStream os)
            throws BadConfigurationException, PPTException {

        String besIP = rs.getInitParameter("BackEndServer");
        if (besIP == null)
            throw new BadConfigurationException("Servlet configuration must included BackEndServer\n");

        String besPort = rs.getInitParameter("BackEndServerPort");
        if (besPort == null)
            throw new BadConfigurationException("Servlet configuration must included BackEndServerPort\n");

        besShowTransaction("version",besIP, Integer.parseInt(besPort) ,os);
    }

    public static void showVersion(String host,
                                   int port,
                                   OutputStream os) throws PPTException {

        besShowTransaction("version",host, port ,os);
    }


    private static void besGetTransaction(String product,
                                          ReqState rs,
                                          OutputStream os)
            throws BadConfigurationException,PPTException {

        System.out.println("Entered besGetTransaction().");

        String besIP = rs.getInitParameter("BackEndServer");
        if (besIP == null)
            throw new BadConfigurationException("Servlet configuration must included BackEndServer\n");

        String besPort = rs.getInitParameter("BackEndServerPort");
        if (besPort == null)
            throw new BadConfigurationException("Servlet configuration must included BackEndServerPort\n");

        OPeNDAPClient oc = new OPeNDAPClient();

        System.out.println("BES at "+besIP+":"+besPort);

        oc.startClient(besIP, Integer.parseInt(besPort));

        if(Debug.isSet("showRequest"))
            oc.setOutput(System.out);
        else {
            DevNull devNull = new DevNull();
            oc.setOutput(devNull);
        }

        String datasetPath = rs.getFileSystemPrefix() + rs.getDataSet();
        String datasetType = "nc";
        String cName = rs.getDataSet();
        String ce = rs.getConstraintExpression();

        String cmd = "set container values "+cName + ", " + datasetPath + ", " + datasetType + ";\n";
        if(Debug.isSet("showRequest")) System.out.print("Sending command: " + cmd);
        oc.executeCommand(cmd);


        System.out.println("ConstraintExpression: "+ce);


        if(ce.equalsIgnoreCase("")){
            cmd = "define d1 as "+rs.getDataSet() + ";\n";
        }
        else {
            cmd = "define d1 as "+rs.getDataSet() + " with "+cName+".constraint=\"" + ce + "\"  ;\n";

        }

        if(Debug.isSet("showRequest")) System.out.print("Sending command: " +cmd);
        oc.executeCommand(cmd);

        cmd = "get "+product+" for d1;\n";
        if(Debug.isSet("showRequest")) System.err.print("Sending command: " +cmd);

        oc.setOutputStomp(os);
        oc.executeCommand(cmd);

        System.out.print("Shutting down client...");

        oc.setOutputStomp(null);

        oc.shutdownClient();
        System.out.println("Done.");

    }



    private static void besShowTransaction(String product,
                                           String host,
                                           int port,
                                           OutputStream os)
            throws PPTException {


        OPeNDAPClient oc = new OPeNDAPClient();

        System.out.println("BES at "+host+":"+port);

        oc.startClient(host, port);

        if(Debug.isSet("showRequest"))
            oc.setOutput(System.out);
        else {
            DevNull devNull = new DevNull();
            oc.setOutput(devNull);
        }

        String cmd = "show "+product+";\n";
        if(Debug.isSet("showRequest")) System.err.print("Sending command: "+cmd);
        oc.setOutputStomp(os);
        oc.executeCommand(cmd);

        System.out.print("Shutting down client...");
        oc.setOutputStomp(null);
        oc.shutdownClient();
        System.out.println("Done.");

    }









}