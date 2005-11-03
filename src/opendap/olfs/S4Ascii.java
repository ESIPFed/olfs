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

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import javax.servlet.http.*;

import opendap.dap.*;
import opendap.util.*;
import opendap.servers.ascii.*;

/**
 * Default handler for OPeNDAP ascii requests. This class is used
 * by OLFS. This code exists as a seperate class in order to alleviate
 * code bloat in the OLFS class. As such, it contains virtually no
 * state, just behaviors.
 *
 * @author Nathan David Potter
 */

public class S4Ascii {

    private static final boolean _Debug = true;


    /**
     * ************************************************************************
     * Default handler for OPeNDAP ascii requests. Returns OPeNDAP data in
     * comma delimited ascii columns for ingestion into some not so
     * OPeNDAP enabled application such as MS-Excel. Accepts constraint expressions
     * in exactly the same way as the regular OPeNDAP dataserver.
     *
     */
    public static void sendASCII(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String dataSet){

        if (Debug.isSet("showResponse"))
            System.out.println("Sending OPeNDAP ASCII Data For: " + dataSet +
                    "    CE: '" + request.getQueryString() + "'");


        String requestURL, ce;
        DConnect url;
        DataDDS dds;

        if (request.getQueryString() == null) {
            ce = "";
        } else {
            ce = "?" + request.getQueryString();
        }

        int suffixIndex = request.getRequestURL().toString().lastIndexOf(".");

        requestURL = request.getRequestURL().substring(0, suffixIndex);

        if (Debug.isSet("showResponse")) {
            System.out.println("New Request URL Resource: '" + requestURL + "'");
            System.out.println("New Request Constraint Expression: '" + ce + "'");
        }

        try {

            if (_Debug) System.out.println("Making connection to .dods service...");
            url = new DConnect(requestURL, true);

            if (_Debug) System.out.println("Requesting data...");
            dds = url.getData(ce, null, new asciiFactory());

            if (_Debug) System.out.println(" ASC DDS: ");
            if (_Debug) dds.print(System.out);

            PrintWriter pw = new PrintWriter(response.getOutputStream());
            PrintWriter pwDebug = new PrintWriter(System.out);

            //pw.println("<pre>");
            dds.print(pw);
            pw.println("---------------------------------------------");


            String s = "";
            Enumeration e = dds.getVariables();

            while (e.hasMoreElements()) {
                BaseType bt = (BaseType) e.nextElement();
                if (_Debug) ((toASCII) bt).toASCII(pwDebug, true, null, true);
                //bt.toASCII(pw,addName,getNAme(),true);
                ((toASCII) bt).toASCII(pw, true, null, true);
            }

            //pw.println("</pre>");
            pw.flush();
            if (_Debug) pwDebug.flush();

        }
        catch (FileNotFoundException fnfe) {
            System.out.println("OUCH! FileNotFoundException: " + fnfe.getMessage());
            fnfe.printStackTrace(System.out);
        }
        catch (MalformedURLException mue) {
            System.out.println("OUCH! MalformedURLException: " + mue.getMessage());
            mue.printStackTrace(System.out);
        }
        catch (IOException ioe) {
            System.out.println("OUCH! IOException: " + ioe.getMessage());
            ioe.printStackTrace(System.out);
        }
        catch (Throwable t) {
            System.out.println("OUCH! Throwable: " + t.getMessage());
            t.printStackTrace(System.out);
        }

        if (_Debug) System.out.println(" dodsASCII done");
    }
    /***************************************************************************/


}




