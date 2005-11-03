/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Server4" project, a Java implementation of the
// OPeNDAP Data Access Protocol.
//
// Copyright (c) 2005 OPeNDAP, Inc.
// Author:  Patrick West <pwest@hao.ucar.edu>
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


package opendap.ppt ;


/**
 * Exception used with DODS client and server request handline.
 *
 * @author Patrick West <A * HREF="mailto:pwest@hao.ucar.edu">pwest@hao.ucar.edu</A>
 */

public class PPTException  extends Exception{
    /**
     * Exception used with DODS client and server request handling where the
     * msg passed to the constructor represents the error that has occurred
     * in handling the DODS request.
     *
     * @param msg The error message assoicated with this exception. In many
     *            cases this message includes exception messages handled
     *            within DODS methods, including client server connection
     *            errors, send and receive error messages, etc...
     */
    public PPTException(String msg) {
        super(msg);
    }
}
