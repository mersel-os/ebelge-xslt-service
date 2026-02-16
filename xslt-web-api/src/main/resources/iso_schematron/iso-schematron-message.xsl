<?xml version="1.0" ?>
<!-- Implmentation for the Schematron XML Schema Language.
	http://www.ascc.net/xml/resource/schematron/schematron.html

 Copyright (c) 2000,2001 Rick Jelliffe and Academia Sinica Computing Center, Taiwan

 This software is provided 'as-is', without any express or implied warranty.
 In no event will the authors be held liable for any damages arising from
 the use of this software.

 Permission is granted to anyone to use this software for any purpose,
 including commercial applications, and to alter it and redistribute it freely,
 subject to the following restrictions:

 1. The origin of this software must not be misrepresented; you must not claim
 that you wrote the original software. If you use this software in a product,
 an acknowledgment in the product documentation would be appreciated but is
 not required.

 2. Altered source versions must be plainly marked as such, and must not be
 misrepresented as being the original software.

 3. This notice may not be removed or altered from any source distribution.
-->

<!-- Schematron message -->

<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:axsl="http://www.w3.org/1999/XSL/TransformAlias"
    xmlns:iso="http://purl.oclc.org/dsdl/schematron"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:osf="http://www.oxygenxml.com/sch/functions"
    exclude-result-prefixes="osf iso">

    <xsl:import href="iso_schematron_skeleton.xsl"/>

    <!-- The language used to generate the massage -->
    <xsl:param name="language" select="if(iso:schema/@xml:lang) then iso:schema/@xml:lang else '#ALL'"/>
    <!-- The diagnostics elements -->
    <xsl:key name="diag" match="iso:diagnostic" use="@id"/>


    <xsl:template name="process-prolog">
        <axsl:output method="xml"/>
    </xsl:template>

    <!-- use default rule for process-root:  copy contents / ignore title -->
    <!-- use default rule for process-pattern: ignore name and see -->
    <!-- use default rule for process-name:  output name -->
    <!-- use default rule for process-assert and process-report:
         call process-message -->

    <xsl:template name="process-message">
        <xsl:param name="pattern"/>
        <xsl:param name="role"/>
        <xsl:param name="diagnostics"/>
        <xsl:param name="type" select="'assert'"/>
        <xsl:param name="see"/>

        <xsl:variable name="actualRole">
            <xsl:choose>
                <xsl:when test="not($role)">
                    <xsl:value-of select="../@role"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$role"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="r"
                      select="translate($actualRole, 'WARNING FATAL INFORMATION ERROR', 'warning fatal information error')"/>
        <Error>
            <xsl:choose>
                <xsl:when test="@subject">
                    <xsl:attribute name="subject" namespace="http://www.oxygenxml.com/ns/schematron">
                        <xsl:value-of select="@subject"/>
                    </xsl:attribute>
                </xsl:when>
                <xsl:when test="../@subject">
                    <xsl:attribute name="subject" namespace="http://www.oxygenxml.com/ns/schematron">
                        <xsl:value-of select="../@subject"/>
                    </xsl:attribute>
                </xsl:when>
            </xsl:choose>
            <xsl:choose>
                <xsl:when test="$r='warning' or $r='warn'">
                    <axsl:text>Warning:</axsl:text>
                </xsl:when>
                <xsl:when test="$r='fatal'">
                    <axsl:text>Fatal:</axsl:text>
                </xsl:when>
                <xsl:when test="$r='error'">
                    <axsl:text>Error:</axsl:text>
                </xsl:when>
                <xsl:when test="$r='info' or $r='information'">
                    <axsl:text>Info:</axsl:text>
                </xsl:when>
            </xsl:choose>

            <xsl:variable name="lang" select="osf:getLang(.)"/>
            <!-- Generate the message from assert only if matches the current language -->
            <xsl:variable name="assertMsg" select="osf:getMessage(., $language, false())"/>

            <!-- Get all diagnostics nodes. -->
            <xsl:variable name="diagnosticNodes" as="item()*">
                <xsl:if test="$diagnostics!=''">
                    <xsl:variable name="assert" select="."/>
                    <xsl:for-each select="tokenize($diagnostics, ' ')">
                        <xsl:sequence select="key('diag', current(), root($assert))"/>
                    </xsl:for-each>
                </xsl:if>
            </xsl:variable>

            <!-- Generate the diagnostics messages for the current language-->
            <xsl:variable name="diagnosticMessages" as="item()*">
                <xsl:for-each select="$diagnosticNodes">
                    <xsl:sequence select="osf:getMessage(., $language, true())"/>
                </xsl:for-each>
            </xsl:variable>

            <!--<xsl:message> $language: <xsl:value-of select="$language"/>
            diag empry <xsl:message select="empty($diagnosticMessages)"/>
            assert msg: <xsl:message select="empty($assertMsg)"/></xsl:message>
            <xsl:message>diag msg '<xsl:sequence select="$diagnosticMessages"/>'</xsl:message>
            <xsl:message>$assertMsg '<xsl:sequence select="$assertMsg"></xsl:sequence>' </xsl:message>-->

            <xsl:choose>
                <xsl:when test="not(empty($diagnosticMessages)) or (not(empty($assertMsg)) and $diagnosticNodes)">
                    <!-- Generate the message for the current language -->
                    <!--<axsl:value-of select="distinct-values()"/>-->
                    <xsl:sequence select="$assertMsg"/>
                    <xsl:sequence select="$diagnosticMessages"/>
                </xsl:when>
                <xsl:when test="$language != '#ALL'">
                    <!-- If no diagnostics for a specific language-->
                    <xsl:choose>
                        <!-- Generate the assertion message for the current language-->
                        <xsl:when test="not(empty($assertMsg))">
                            <xsl:sequence select="$assertMsg"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- Generate the messages for all languages. -->
                            <xsl:variable name="assertMsg" select="osf:getMessage(., '#ALL', false())"/>
                            <!-- Print assertion message -->
                            <xsl:sequence select="$assertMsg"/>
                            <!-- Print distinct diagnostics messages. -->
                            <xsl:for-each select="$diagnosticNodes">
                                <xsl:sequence select="osf:getMessage(., '#ALL', true())"/>
                            </xsl:for-each>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Generate the assertion message, if no language match-->
                    <xsl:sequence select="osf:getMessage(., '#ALL', false())"/>
                </xsl:otherwise>
            </xsl:choose>

            <!--<axsl:text> (</axsl:text>
            <xsl:value-of select="$pattern" />
            <xsl:if test="$role">
               <axsl:text> / </axsl:text>
               <xsl:value-of select="$role"/>
            </xsl:if>
            <axsl:text>)</axsl:text>-->
            <!--<axsl:text> [</axsl:text>
            <xsl:value-of select="$type"/>
            <axsl:text>]</axsl:text>-->
            <xsl:if test="$see">
                <axsl:text>&#10;URL:<xsl:value-of select="$see"/>
                </axsl:text>
            </xsl:if>
            <xsl:call-template name="process-message-end"/>
        </Error>
    </xsl:template>

    <!-- Function used to obtain the message from the current node -->
    <xsl:function name="osf:getMessage" as="item()*">
        <xsl:param name="node" as="node()" required="yes"/>
        <xsl:param name="messagesLang" as="xs:string" required="yes"/>
        <xsl:param name="isDiag" as="xs:boolean"/>
        <xsl:variable name="lang" select="osf:getLang($node)"/>

        <xsl:choose>
            <xsl:when test="$messagesLang != '#ALL'">
                <!-- Generate the message from assert only if matches the current language -->
                <xsl:if test="starts-with($lang, $language)">
                    <xsl:if test="$isDiag">[#diag]</xsl:if>
                    <xsl:apply-templates select="$node/child::node()" mode="text"/>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <!-- Generate the message with the language in front. -->
                <xsl:if test="$isDiag">[#diag]</xsl:if>
                <xsl:if test="$node/@xml:lang">[<xsl:value-of select="$node/@xml:lang"/>]
                </xsl:if>
                <xsl:apply-templates select="$node/child::node()" mode="text"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <!-- Get the language of the current node -->
    <xsl:function name="osf:getLang" as="xs:string">
        <xsl:param name="node" as="node()"/>
        <xsl:variable name="lang" select="($node/ancestor-or-self::*/@xml:lang)[last()]"/>
        <xsl:value-of select="if ($lang) then ($lang) else ('#NONE')"/>
    </xsl:function>

    <!-- Can be overridden to generate the quick fix ids in the message. -->
    <xsl:template name="process-message-end"/>

</xsl:stylesheet>
