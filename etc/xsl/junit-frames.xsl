<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
        xmlns:lxslt="http://xml.apache.org/xslt"
        xmlns:redirect="org.apache.xalan.xslt.extensions.Redirect"
        extension-element-prefixes="redirect">
<xsl:output method="html" indent="yes" encoding="US-ASCII"/>
<xsl:decimal-format decimal-separator="." grouping-separator=","/>
<!--
 The Apache Software License, Version 1.1

 Copyright (c) 2001-2002 The Apache Software Foundation.  All rights
 reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in
    the documentation and/or other materials provided with the
    distribution.

 3. The end-user documentation included with the redistribution, if
    any, must include the following acknowlegement:
       "This product includes software developed by the
        Apache Software Foundation (http://www.apache.org/)."
    Alternately, this acknowlegement may appear in the software itself,
    if and wherever such third-party acknowlegements normally appear.

 4. The names "The Jakarta Project", "Ant", and "Apache Software
    Foundation" must not be used to endorse or promote products derived
    from this software without prior written permission. For written
    permission, please contact apache@apache.org.

 5. Products derived from this software may not be called "Apache"
    nor may "Apache" appear in their names without prior written
    permission of the Apache Group.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.
 ====================================================================

 This software consists of voluntary contributions made by many
 individuals on behalf of the Apache Software Foundation.  For more
 information on the Apache Software Foundation, please see
 <http://www.apache.org/>.
 -->

<!--

 Sample stylesheet to be used with An JUnitReport output.

 It creates a set of HTML files a la javadoc where you can browse easily
 through all packages and classes.

 @author Stephane Bailliez <a href="mailto:sbailliez@apache.org"/>
 @author Erik Hatcher <a href="mailto:ehatcher@apache.org"/>

-->
<xsl:param name="output.dir" select="'.'"/>


<xsl:template match="testsuites">
        <!-- create the index.html -->
        <redirect:write file="{$output.dir}/index.html">
                <xsl:call-template name="index.html"/>
        </redirect:write>

        <!-- create the stylesheet.css -->
        <redirect:write file="{$output.dir}/stylesheet.css">
                <xsl:call-template name="stylesheet.css"/>
        </redirect:write>

        <!-- create the overview-packages.html at the root -->
        <redirect:write file="{$output.dir}/overview-summary.html">
                <xsl:apply-templates select="." mode="overview.packages"/>
        </redirect:write>

        <!-- create the all-packages.html at the root -->
        <redirect:write file="{$output.dir}/overview-frame.html">
                <xsl:apply-templates select="." mode="all.packages"/>
        </redirect:write>

        <!-- create the all-classes.html at the root -->
        <redirect:write file="{$output.dir}/allclasses-frame.html">
                <xsl:apply-templates select="." mode="all.classes"/>
        </redirect:write>

        <!-- process all packages -->
        <xsl:for-each select="./testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
                <xsl:call-template name="package">
                        <xsl:with-param name="name" select="@package"/>
                </xsl:call-template>
        </xsl:for-each>
</xsl:template>


<xsl:template name="package">
        <xsl:param name="name"/>
        <xsl:variable name="package.dir">
                <xsl:if test="not($name = '')"><xsl:value-of select="translate($name,'.','/')"/></xsl:if>
                <xsl:if test="$name = ''">.</xsl:if>
        </xsl:variable>
        <!--Processing package <xsl:value-of select="@name"/> in <xsl:value-of select="$output.dir"/> -->
        <!-- create a classes-list.html in the package directory -->
        <redirect:write file="{$output.dir}/{$package.dir}/package-frame.html">
                <xsl:call-template name="classes.list">
                        <xsl:with-param name="name" select="$name"/>
                </xsl:call-template>
        </redirect:write>

        <!-- create a package-summary.html in the package directory -->
        <redirect:write file="{$output.dir}/{$package.dir}/package-summary.html">
                <xsl:call-template name="package.summary">
                        <xsl:with-param name="name" select="$name"/>
                </xsl:call-template>
        </redirect:write>

        <!-- for each class, creates a @name.html -->
        <!-- @bug there will be a problem with inner classes having the same name, it will be overwritten -->
        <xsl:for-each select="/testsuites/testsuite[@package = $name]">
                <redirect:write file="{$output.dir}/{$package.dir}/{@name}.html">
                        <xsl:apply-templates select="." mode="class.details"/>
                </redirect:write>
        </xsl:for-each>
</xsl:template>

<xsl:template name="index.html">
<html>
        <head>
                <title>Unit Test Results.</title>
        </head>
        <frameset cols="20%,80%">
                <frameset rows="30%,70%">
                        <frame src="overview-frame.html" name="packageListFrame"/>
                        <frame src="allclasses-frame.html" name="classListFrame"/>
                </frameset>
                <frame src="overview-summary.html" name="classFrame"/>
        </frameset>
        <noframes>
                <h2>Frame Alert</h2>
                <p>
                This document is designed to be viewed using the frames feature. If you see this message, you are using a non-frame-capable web client.
                </p>
        </noframes>
</html>
</xsl:template>

<!-- this is the stylesheet css to use for nearly everything -->
<xsl:template name="stylesheet.css">
body {
        font:normal 68% verdana,arial,helvetica;
        color:#000000;
}
table tr td, table tr th {
    font-size: 68%;
}
table.details tr th{
        font-weight: bold;
        text-align:left;
        background:#a6caf0;
}
table.details tr td{
        background:#eeeee0;
}

p {
        line-height:1.5em;
        margin-top:0.5em; margin-bottom:1.0em;
}
h1 {
        margin: 0px 0px 5px; font: 165% verdana,arial,helvetica
}
h2 {
        margin-top: 1em; margin-bottom: 0.5em; font: bold 125% verdana,arial,helvetica
}
h3 {
        margin-bottom: 0.5em; font: bold 115% verdana,arial,helvetica
}
h4 {
        margin-bottom: 0.5em; font: bold 100% verdana,arial,helvetica
}
h5 {
        margin-bottom: 0.5em; font: bold 100% verdana,arial,helvetica
}
h6 {
        margin-bottom: 0.5em; font: bold 100% verdana,arial,helvetica
}
.Error {
        font-weight:bold; color:red;
}
.Failure {
        font-weight:bold; color:purple;
}
.Properties {
  text-align:right;
}
</xsl:template>


<!-- ======================================================================
    This page is created for every testsuite class.
    It prints a summary of the testsuite and detailed information about
    testcase methods.
     ====================================================================== -->
<xsl:template match="testsuite" mode="class.details">
        <xsl:variable name="package.name" select="@package"/>
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name" select="$package.name"/>
                        </xsl:call-template>
      <script language="JavaScript">
        var TestCases = new Array();
        var cur;
        <xsl:apply-templates select="properties"/>
       </script>
       <script language="JavaScript"><![CDATA[
        function displayProperties (name) {
          var win = window.open('','JUnitSystemProperties','scrollbars=1,resizable=1');
          var doc = win.document.open();
          doc.write("<html><head><title>Properties of " + name + "</title>");
          doc.write("<style>")
          doc.write("body {font:normal 68% verdana,arial,helvetica;     color:#000000; }");
          doc.write("table tr td, table tr th { font-size: 68%; }");
          doc.write("table.properties { border-collapse:collapse; border-left:solid 1 #cccccc; border-top:solid 1 #cccccc; padding:5px; }");
          doc.write("table.properties th { text-align:left; border-right:solid 1 #cccccc; border-bottom:solid 1 #cccccc; background-color:#eeeeee; }");
          doc.write("table.properties td { font:normal; text-align:left; border-right:solid 1 #cccccc; border-bottom:solid 1 #cccccc; background-color:#fffffff; }");
          doc.write("h3 { margin-bottom: 0.5em; font: bold 115% verdana,arial,helvetica }");
          doc.write("</style>");
          doc.write("</head><body>");
          doc.write("<h3>Properties of " + name + "</h3>");
          doc.write("<div align=\"right\"><a href=\"javascript:window.close();\">Close</a></div>");
          doc.write("<table class='properties'>");
          doc.write("<tr><th>Name</th><th>Value</th></tr>");
          for (prop in TestCases[name]) {
            doc.write("<tr><th>" + prop + "</th><td>" + TestCases[name][prop] + "</td></tr>");
          }
          doc.write("</table>");
          doc.write("</body></html>");
          doc.close();
          win.focus();
        }
      ]]>
      </script>
                </head>
                <body>
                        <xsl:call-template name="pageHeader"/>
                        <h3>Class <xsl:if test="not($package.name = '')"><xsl:value-of select="$package.name"/>.</xsl:if><xsl:value-of select="@name"/></h3>


                        <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
                                <xsl:call-template name="testsuite.test.header"/>
                                <xsl:apply-templates select="." mode="print.test"/>
                        </table>

                        <h2>Tests</h2>
                        <p>
                        <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
        <xsl:call-template name="testcase.test.header"/>
                          <!--
                          test can even not be started at all (failure to load the class)
                          so report the error directly
                          -->
                                <xsl:if test="./error">
                                        <tr class="Error">
                                                <td colspan="4"><xsl:apply-templates select="./error"/></td>
                                        </tr>
                                </xsl:if>
                                <xsl:apply-templates select="./testcase" mode="print.test"/>
                        </table>
                        </p>
      <div class="Properties">
        <a>
          <xsl:attribute name="href">javascript:displayProperties('<xsl:value-of select="@package"/>.<xsl:value-of select="@name"/>');</xsl:attribute>
          Properties &#187;
        </a>
      </div>

      <xsl:apply-templates select="./system-out"/>
      <xsl:apply-templates select="./system-err"/>
                </body>
        </html>
</xsl:template>

  <!--
   Write properties into a JavaScript data structure.
   This is based on the original idea by Erik Hatcher (ehatcher@apache.org)
   -->
  <xsl:template match="properties">
    cur = TestCases['<xsl:value-of select="../@package"/>.<xsl:value-of select="../@name"/>'] = new Array();
        <xsl:for-each select="property">
    <xsl:sort select="@name"/>
        cur['<xsl:value-of select="@name"/>'] = '<xsl:call-template name="JS-escape"><xsl:with-param name="string" select="@value"/></xsl:call-template>';
        </xsl:for-each>
  </xsl:template>


<!-- ======================================================================
    This page is created for every package.
    It prints the name of all classes that belongs to this package.
    @param name the package name to print classes.
     ====================================================================== -->
<!-- list of classes in a package -->
<xsl:template name="classes.list">
        <xsl:param name="name"/>
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name" select="$name"/>
                        </xsl:call-template>
                </head>
                <body>
                        <table width="100%">
                                <tr>
                                        <td nowrap="nowrap">
                                                <h2><a href="package-summary.html" target="classFrame"><xsl:value-of select="$name"/></a></h2>
                                        </td>
                                </tr>
                        </table>

                        <h2>Classes</h2>
                        <p>
                        <table width="100%">
                                <xsl:for-each select="/testsuites/testsuite[./@package = $name]">
                                        <xsl:sort select="@name"/>
                                        <tr>
                                                <td nowrap="nowrap">
                                                        <a href="{@name}.html" target="classFrame"><xsl:value-of select="@name"/></a>
                                                </td>
                                        </tr>
                                </xsl:for-each>
                        </table>
                        </p>
                </body>
        </html>
</xsl:template>


<!--
        Creates an all-classes.html file that contains a link to all package-summary.html
        on each class.
-->
<xsl:template match="testsuites" mode="all.classes">
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name"/>
                        </xsl:call-template>
                </head>
                <body>
                        <h2>Classes</h2>
                        <p>
                        <table width="100%">
                                <xsl:apply-templates select="testsuite" mode="all.classes">
                                        <xsl:sort select="@name"/>
                                </xsl:apply-templates>
                        </table>
                        </p>
                </body>
        </html>
</xsl:template>

<xsl:template match="testsuite" mode="all.classes">
        <xsl:variable name="package.name" select="@package"/>
        <tr>
                <td nowrap="nowrap">
                        <a target="classFrame">
                                <xsl:attribute name="href">
                                        <xsl:if test="not($package.name='')">
                                                <xsl:value-of select="translate($package.name,'.','/')"/><xsl:text>/</xsl:text>
                                        </xsl:if><xsl:value-of select="@name"/><xsl:text>.html</xsl:text>
                                </xsl:attribute>
                                <xsl:value-of select="@name"/>
                        </a>
                </td>
        </tr>
</xsl:template>


<!--
        Creates an html file that contains a link to all package-summary.html files on
        each package existing on testsuites.
        @bug there will be a problem here, I don't know yet how to handle unnamed package :(
-->
<xsl:template match="testsuites" mode="all.packages">
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name"/>
                        </xsl:call-template>
                </head>
                <body>
                        <h2><a href="overview-summary.html" target="classFrame">Home</a></h2>
                        <h2>Packages</h2>
                        <p>
                                <table width="100%">
                                        <xsl:apply-templates select="testsuite[not(./@package = preceding-sibling::testsuite/@package)]" mode="all.packages">
                                                <xsl:sort select="@package"/>
                                        </xsl:apply-templates>
                                </table>
                        </p>
                </body>
        </html>
</xsl:template>

<xsl:template match="testsuite" mode="all.packages">
        <tr>
                <td nowrap="nowrap">
                        <a href="{translate(@package,'.','/')}/package-summary.html" target="classFrame">
                                <xsl:value-of select="@package"/>
                        </a>
                </td>
        </tr>
</xsl:template>


<xsl:template match="testsuites" mode="overview.packages">
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name"/>
                        </xsl:call-template>
                </head>
                <body>
                <xsl:attribute name="onload">open('allclasses-frame.html','classListFrame')</xsl:attribute>
                <xsl:call-template name="pageHeader"/>
                <h2>Summary</h2>
                <xsl:variable name="testCount" select="sum(testsuite/@tests)"/>
                <xsl:variable name="errorCount" select="sum(testsuite/@errors)"/>
                <xsl:variable name="failureCount" select="sum(testsuite/@failures)"/>
                <xsl:variable name="timeCount" select="sum(testsuite/@time)"/>
                <xsl:variable name="successRate" select="($testCount - $failureCount - $errorCount) div $testCount"/>
                <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
                <tr valign="top">
                        <th>Tests</th>
                        <th>Failures</th>
                        <th>Errors</th>
                        <th>Success rate</th>
                        <th>Time</th>
                </tr>
                <tr valign="top">
                        <xsl:attribute name="class">
                                <xsl:choose>
                                        <xsl:when test="$errorCount &gt; 0">Error</xsl:when>
                                        <xsl:when test="$failureCount &gt; 0">Failure</xsl:when>
                                        <xsl:otherwise>Pass</xsl:otherwise>
                                </xsl:choose>
                        </xsl:attribute>
                        <td><xsl:value-of select="$testCount"/></td>
                        <td><xsl:value-of select="$failureCount"/></td>
                        <td><xsl:value-of select="$errorCount"/></td>
                        <td>
                                <xsl:call-template name="display-percent">
                                        <xsl:with-param name="value" select="$successRate"/>
                                </xsl:call-template>
                        </td>
                        <td>
                                <xsl:call-template name="display-time">
                                        <xsl:with-param name="value" select="$timeCount"/>
                                </xsl:call-template>
                        </td>

                </tr>
                </table>
                <table border="0" width="95%">
                <tr>
                <td     style="text-align: justify;">
                Note: <em>failures</em> are anticipated and checked for with assertions while <em>errors</em> are unanticipated.
                </td>
                </tr>
                </table>

                <h2>Packages</h2>
                <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
                        <xsl:call-template name="testsuite.test.header"/>
                        <xsl:for-each select="testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
                                <xsl:sort select="@package" order="ascending"/>
                                <!-- get the node set containing all testsuites that have the same package -->
                                <xsl:variable name="insamepackage" select="/testsuites/testsuite[./@package = current()/@package]"/>
                                <tr valign="top">
                                        <!-- display a failure if there is any failure/error in the package -->
                                        <xsl:attribute name="class">
                                                <xsl:choose>
                                                        <xsl:when test="sum($insamepackage/@errors) &gt; 0">Error</xsl:when>
                                                        <xsl:when test="sum($insamepackage/@failures) &gt; 0">Failure</xsl:when>
                                                        <xsl:otherwise>Pass</xsl:otherwise>
                                                </xsl:choose>
                                        </xsl:attribute>
                                        <td><a href="{translate(@package,'.','/')}/package-summary.html"><xsl:value-of select="@package"/></a></td>
                                        <td><xsl:value-of select="sum($insamepackage/@tests)"/></td>
                                        <td><xsl:value-of select="sum($insamepackage/@errors)"/></td>
                                        <td><xsl:value-of select="sum($insamepackage/@failures)"/></td>
                                        <td>
                                        <xsl:call-template name="display-time">
                                                <xsl:with-param name="value" select="sum($insamepackage/@time)"/>
                                        </xsl:call-template>
                                        </td>
                                </tr>
                        </xsl:for-each>
                </table>
                </body>
                </html>
</xsl:template>


<xsl:template name="package.summary">
        <xsl:param name="name"/>
        <html>
                <head>
                        <xsl:call-template name="create.stylesheet.link">
                                <xsl:with-param name="package.name" select="$name"/>
                        </xsl:call-template>
                </head>
                <body>
                        <xsl:attribute name="onload">open('package-frame.html','classListFrame')</xsl:attribute>
                        <xsl:call-template name="pageHeader"/>
                        <h3>Package <xsl:value-of select="$name"/></h3>

                        <!--table border="0" cellpadding="5" cellspacing="2" width="95%">
                                <xsl:call-template name="class.metrics.header"/>
                                <xsl:apply-templates select="." mode="print.metrics"/>
                        </table-->

                        <xsl:variable name="insamepackage" select="/testsuites/testsuite[./@package = $name]"/>
                        <xsl:if test="count($insamepackage) &gt; 0">
                                <h2>Classes</h2>
                                <p>
                                <table class="details" border="0" cellpadding="5" cellspacing="2" width="95%">
                                        <xsl:call-template name="testsuite.test.header"/>
                                        <xsl:apply-templates select="$insamepackage" mode="print.test">
                                                <xsl:sort select="@name"/>
                                        </xsl:apply-templates>
                                </table>
                                </p>
                        </xsl:if>
                </body>
        </html>
</xsl:template>


<!--
    transform string like a.b.c to ../../../
    @param path the path to transform into a descending directory path
-->
<xsl:template name="path">
        <xsl:param name="path"/>
        <xsl:if test="contains($path,'.')">
                <xsl:text>../</xsl:text>
                <xsl:call-template name="path">
                        <xsl:with-param name="path"><xsl:value-of select="substring-after($path,'.')"/></xsl:with-param>
                </xsl:call-template>
        </xsl:if>
        <xsl:if test="not(contains($path,'.')) and not($path = '')">
                <xsl:text>../</xsl:text>
        </xsl:if>
</xsl:template>


<!-- create the link to the stylesheet based on the package name -->
<xsl:template name="create.stylesheet.link">
        <xsl:param name="package.name"/>
        <link rel="stylesheet" type="text/css" title="Style"><xsl:attribute name="href"><xsl:if test="not($package.name = 'unnamed package')"><xsl:call-template name="path"><xsl:with-param name="path" select="$package.name"/></xsl:call-template></xsl:if>stylesheet.css</xsl:attribute></link>
</xsl:template>


<!-- Page HEADER -->
<xsl:template name="pageHeader">
        <h1>Unit Test Results</h1>
        <table width="100%">
        <tr>
                <td align="left"></td>
                <td align="right">Designed for use with <a href='http://www.junit.org'>JUnit</a> and <a href='http://jakarta.apache.org'>Ant</a>.</td>
        </tr>
        </table>
        <hr size="1"/>
</xsl:template>

<!-- class header -->
<xsl:template name="testsuite.test.header">
        <tr valign="top">
                <th width="80%">Name</th>
                <th>Tests</th>
                <th>Errors</th>
                <th>Failures</th>
                <th nowrap="nowrap">Time(s)</th>
        </tr>
</xsl:template>

<!-- method header -->
<xsl:template name="testcase.test.header">
        <tr valign="top">
                <th>Name</th>
                <th>Status</th>
                <th width="80%">Type</th>
                <th nowrap="nowrap">Time(s)</th>
        </tr>
</xsl:template>

<!-- System Output -->
<xsl:template match="system-out">
  <xsl:call-template name="show-system-output">
    <xsl:with-param name="title">System.out</xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!-- System Err Output -->
<xsl:template match="system-err">
  <xsl:call-template name="show-system-output">
    <xsl:with-param name="title">System.err</xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!-- Print a Header and the output only if there is output to show -->
<xsl:template name="show-system-output">
  <xsl:param name="title"/>
  <xsl:choose>
    <xsl:when test="string-length(.) > 0">
      <h2><xsl:value-of select="$title"/> output</h2>
        <code>
                <p/>
                <xsl:call-template name="br-replace">
                        <xsl:with-param name="word" select="."/>
                </xsl:call-template>
        </code>
    </xsl:when>
  </xsl:choose>
</xsl:template>

<!-- class information -->
<xsl:template match="testsuite" mode="print.test">
        <tr valign="top">
                <xsl:attribute name="class">
                        <xsl:choose>
                                <xsl:when test="@errors[.&gt; 0]">Error</xsl:when>
                                <xsl:when test="@failures[.&gt; 0]">Failure</xsl:when>
                                <xsl:otherwise>Pass</xsl:otherwise>
                        </xsl:choose>
                </xsl:attribute>
                <td><a href="{@name}.html"><xsl:value-of select="@name"/></a></td>
                <td><xsl:apply-templates select="@tests"/></td>
                <td><xsl:apply-templates select="@errors"/></td>
                <td><xsl:apply-templates select="@failures"/></td>
                <td><xsl:call-template name="display-time">
                                <xsl:with-param name="value" select="@time"/>
                        </xsl:call-template>
        </td>
        </tr>
</xsl:template>

<xsl:template match="testcase" mode="print.test">
        <tr valign="top">
            <xsl:attribute name="class">
                        <xsl:choose>
                                <xsl:when test="error">Error</xsl:when>
                                <xsl:when test="failure">Failure</xsl:when>
                                <xsl:otherwise>TableRowColor</xsl:otherwise>
                        </xsl:choose>
                </xsl:attribute>
                <td><xsl:value-of select="@name"/></td>
                <xsl:choose>
                        <xsl:when test="failure">
                                <td>Failure</td>
                                <td><xsl:apply-templates select="failure"/></td>
                        </xsl:when>
                        <xsl:when test="error">
                                <td>Error</td>
                                <td><xsl:apply-templates select="error"/></td>
                        </xsl:when>
                        <xsl:otherwise>
                                <td>Success</td>
                                <td></td>
                        </xsl:otherwise>
                </xsl:choose>
                <td>
                    <xsl:call-template name="display-time">
                <xsl:with-param name="value" select="@time"/>
                    </xsl:call-template>
                </td>
    </tr>
</xsl:template>


<!-- Note : the below template error and failure are the same style
            so just call the same style store in the toolkit template -->
<xsl:template match="failure">
        <xsl:call-template name="display-failures"/>
</xsl:template>

<xsl:template match="error">
        <xsl:call-template name="display-failures"/>
</xsl:template>

<!-- Style for the error and failure in the testcase template -->
<xsl:template name="display-failures">
        <xsl:choose>
                <xsl:when test="not(@message)">N/A</xsl:when>
                <xsl:otherwise>
                        <xsl:value-of select="@message"/>
                </xsl:otherwise>
        </xsl:choose>
        <!-- display the stacktrace -->
        <code>
                <p/>
                <xsl:call-template name="br-replace">
                        <xsl:with-param name="word" select="."/>
                </xsl:call-template>
        </code>
        <!-- the latter is better but might be problematic for non-21" monitors... -->
        <!--pre><xsl:value-of select="."/></pre-->
</xsl:template>

<xsl:template name="JS-escape">
        <xsl:param name="string"/>
        <xsl:choose>
                <xsl:when test="contains($string,&quot;'&quot;)">
                        <xsl:value-of select="substring-before($string,&quot;'&quot;)"/>\&apos;<xsl:call-template name="JS-escape">
                                <xsl:with-param name="string" select="substring-after($string,&quot;'&quot;)"/>
                        </xsl:call-template>
                </xsl:when>
                <xsl:when test="contains($string,'\')">
                        <xsl:value-of select="substring-before($string,'\')"/>\\<xsl:call-template name="JS-escape">
                                <xsl:with-param name="string" select="substring-after($string,'\')"/>
                        </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                        <xsl:value-of select="$string"/>
                </xsl:otherwise>
        </xsl:choose>
</xsl:template>


<!--
        template that will convert a carriage return into a br tag
        @param word the text from which to convert CR to BR tag
-->
<xsl:template name="br-replace">
        <xsl:param name="word"/>
        <xsl:choose>
                <xsl:when test="contains($word,'&#xA;')">
                        <xsl:value-of select="substring-before($word,'&#xA;')"/>
                        <br/>
                        <xsl:call-template name="br-replace">
                                <xsl:with-param name="word" select="substring-after($word,'&#xA;')"/>
                        </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                        <xsl:value-of select="$word"/>
                </xsl:otherwise>
        </xsl:choose>
</xsl:template>

<xsl:template name="display-time">
        <xsl:param name="value"/>
        <xsl:value-of select="format-number($value,'0.000')"/>
</xsl:template>

<xsl:template name="display-percent">
        <xsl:param name="value"/>
        <xsl:value-of select="format-number($value,'0.00%')"/>
</xsl:template>
</xsl:stylesheet>

