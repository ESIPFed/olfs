<%@ page import="opendap.coreServlet.OPeNDAPException" %>
<%--
  ~ /////////////////////////////////////////////////////////////////////////////
  ~ // This file is part of the "Hyrax Data Server" project.
  ~ //
  ~ //
  ~ // Copyright (c) 2013 OPeNDAP, Inc.
  ~ // Author: Nathan David Potter  <ndp@opendap.org>
  ~ //
  ~ // This library is free software; you can redistribute it and/or
  ~ // modify it under the terms of the GNU Lesser General Public
  ~ // License as published by the Free Software Foundation; either
  ~ // version 2.1 of the License, or (at your option) any later version.
  ~ //
  ~ // This library is distributed in the hope that it will be useful,
  ~ // but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  ~ // Lesser General Public License for more details.
  ~ //
  ~ // You should have received a copy of the GNU Lesser General Public
  ~ // License along with this library; if not, write to the Free Software
  ~ // Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
  ~ //
  ~ // You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
  ~ /////////////////////////////////////////////////////////////////////////////
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@page session="false" %>
<%
    String contextPath = request.getContextPath();

    String message = OPeNDAPException.getAndClearCachedErrorMessage();

%>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <link rel='stylesheet' href='<%= contextPath %>/docs/css/contents.css' type='text/css'/>
    <title>Hyrax: Resource Not Found</title>
</head>

<body>
<p align="left">&nbsp;</p>

<h1 align="center">Hyrax - BES Timeout (504) </h1>
<hr align="left" size="1" noshade="noshade"/>
<table width="100%" border="0">
    <tr>
        <td>
            <a href="<%= contextPath %>/docs/images/watchit.jpg">
                <img src="<%= contextPath %>/docs/images/watchit.jpg"
                     alt="BES Timeout!"
                     title="BES Timeout!"
                     width="360" height="480"
                     border="0"/>
            </a>
        </td>

        <td>
            <p align="left">I'm sorry, the request you made just took way too long.</p>

            <% if(message != null) { %>
            <p align="left">The specific error message associated with your request was:</p>
            <blockquote>
                <p>
                    <strong><%= message %></strong>
                </p>
            </blockquote>
            <% } %>
        </td>
    </tr>
</table>
<hr align="left" size="1" noshade="noshade"/>
<h1 align="center">Hyrax - BES Timeout (504) </h1>
</body>
</html>
