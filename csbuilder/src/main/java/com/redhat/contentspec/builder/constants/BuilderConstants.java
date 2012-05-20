package com.redhat.contentspec.builder.constants;

public class BuilderConstants {
	
	/** Number of times to try setting the property tags on the topics */
	public static final Integer MAXIMUM_SET_PROP_TAGS_RETRY = 5;
	public static final Integer MAXIMUM_SET_PROP_TAG_NAME_RETRY = 50;
	
	public static final String BUILDER_VERSION			= "1.4";
	public static final String BUILD_NAME				= "CSProcessor Builder Version " + BUILDER_VERSION;
	
	public static final String RESOURCE_LOCATION		= "/";
	public static final String DOCBOOK_DTD_LOCATION		= "/docbook-xml-4.5/";
	public static final String DOCBOOK_45_RESOURCE_LOCATION = "/Docbook4-5/";
	
	public static final String DOCBOOK_45_DTD = "docbookx.dtd";
	public static final String ROCBOOK_45_DTD = "rocbookx.dtd";
	
	/** A prefix for error xref ids */
	public static final String ERROR_XREF_ID_PREFIX = "TagErrorXRef";
	
	public static final String ESCAPED_TITLE_REGEX 		= "<<contentSpec\\.escapedTitle>>";
	public static final String TITLE_REGEX 				= "<<contentSpec\\.title>>";
	public static final String BRAND_REGEX 				= "<<contentSpec\\.brand>>";
	public static final String VERSION_REGEX 			= "<<contentSpec\\.version>>";
	public static final String PRODUCT_REGEX 			= "<<contentSpec\\.product>>";
	public static final String EDITION_REGEX 			= "<<contentSpec\\.edition>>";
	public static final String PUBSNUMBER_REGEX			= "<<contentSpec\\.pubsNumber>>";
	public static final String SUBTITLE_REGEX 			= "<<contentSpec\\.subtitle>>";
	public static final String BZPRODUCT_REGEX 			= "<<contentSpec\\.bzproduct>>";
	public static final String BZCOMPONENT_REGEX 		= "<<contentSpec\\.bzcomponent>>";
	public static final String BUILDER_VERSION_REGEX 	= "<<csBuilder\\.version>>";
	public static final String DATE_FORMAT_REGEX		= "Day Mon DD HH:MM:SS YYYY";
	public static final String REV_DATE_FORMAT_REGEX	= "DAY MON DD YYYY";
	public static final String DATE_STRING_FORMAT		= "EEE MMM dd HH:mm:ss yyyy";
	public static final String REV_DATE_STRING_FORMAT	= "EEE MMM dd yyyy";
	public static final String YEAR_FORMAT_REGEX		= "YYYY";
	public static final String TOPIC_ID_REGEX			= "<!-- Inject TopicID -->";
	public static final String TOPIC_TITLE_REGEX		= "<!-- Inject TopicTitle -->";
	public static final String ERROR_XREF_REGEX			= "<!-- Inject ErrorXREF -->";
	public static final String PREFACE_REGEX			= "<!-- Inject Preface -->";
	public static final String LEGAL_NOTICE_REGEX		= "<!-- Inject Legal Notice -->";
	public static final String ABSTRACT_REGEX			= "<!-- Inject Abstract -->";
	
	public static final String XIINCLUDES_INJECTION_STRING = "<!-- Inject XIIncludes -->";
	
	public static final String CONTENT_SPEC_ID_REGEX	= "<<contentSpec\\.ID>>"; 
	public static final String CONTENT_SPEC_REV_REGEX	= "<<contentSpec\\.Rev>>";
	public static final String CONTENT_SPEC_COPYRIGHT_REGEX 	= "<<contentSpec\\.copyrightHolder>>";
	public static final String CONTENT_SPEC_BUGZILLA_URL_REGEX	= "<<contentSpec\\.bugzillaUrl>>";
	
	public static final String DEFAULT_ABSTRACT			= 	"<abstract>\n\t\t<para>\n" +
			"\t\t\tA brief paragraph describing this book. This will be used as the description for the rpm package.\n" +
			"\t\t</para>\n" +
			"\t</abstract>\n";
	
	// Revision_History.xml regex constants
	public static final String AUTHOR_FIRST_NAME_REGEX	= "<!-- Inject authorInformation\\.firstName -->";
	public static final String AUTHOR_SURNAME_REGEX		= "<!-- Inject authorInformation\\.lastName -->";
	public static final String AUTHOR_EMAIL_REGEX		= "<!-- Inject authorInformation\\.email -->";

	public static final String BRAND_DEFAULT			= "common";
	public static final String SUBTITLE_DEFAULT			= "Subtitle goes here";
	public static final String EDITION_DEFAULT			= "0";
	public static final String PUBSNUMBER_DEFAULT		= "0";
	
	public static final String DEFAULT_AUTHOR_FIRSTNAME	= "CS Builder";
	public static final String DEFAULT_AUTHOR_LASTNAME	= "Robot";
	public static final String DEFAULT_EMAIL			= "robot@dev.null.com";
	
	public static final String INVALID_TOPIC_XML		= "Topic failed schema validation. The error was <emphasis>%s</emphasis>";
	public static final String EMPTY_TOPIC_XML			= "Topic has no XML data";
	public static final String INVALID_UTF8_CHARACTER 	= "Invalid UTF-8 character found! You may have issues building the content specification if not fixed";
	public static final String BAD_XML_STRUCTURE 		= "Topic doesn't have well-formed xml";
	public static final String FAILED_TOPIC_XML			= "Topic failed relationship and bug reporting injection/validation. The error was <emphasis>%s</emphasis>";
	public static final String BUILT_MSG				= "Built from Content Specification: %d, Revision: %d";
	public static final String BUILT_FILE_MSG			= "Content Specification built from file";
	
	// Defaults
	public static final String DEFAULT_CS_DECRIPTION	= "A brief paragraph describing this book. This will be used as the description for the rpm package.";
	public static final String DEFAULT_BZCOMPONENT		= "documentation";
	public static final boolean DEFAULT_PERMISSIVE_MODE = false;
	public static final boolean DEFAULT_BUGZILLA_INJECTION_MODE	= true;
	
	public static final String VERBATIM_XML_ELEMENTS	= "screen,programlisting";
	public static final String INLINE_XML_ELEMENTS	= "code,prompt,command,firstterm,ulink,guilabel,filename,replaceable,parameter,literal,classname,sgmltag,guibutton,guimenuitem,guimenu,menuchoice,citetitle,revnumber,application,systemitem";
	public static final String CONTENTS_INLINE_XML_ELEMENTS = "title,term";
	
	public static final String CS_NAME_ENT_FILE = "<!ENTITY PRODUCT \"<<contentSpec.product>>\">\n" +
												"<!ENTITY BOOKID \"<<contentSpec.escapedTitle>>\">\n" +
												"<!ENTITY YEAR \"YYYY\">\n" +
												"<!ENTITY TITLE \"<<contentSpec.Title>>\">\n" +
												"<!ENTITY HOLDER \"<<contentSpec.copyrightHolder>>\">\n" +
												"<!ENTITY BZURL \"<<contentSpec.bugzillaUrl>>\">\n" + 
												"<!ENTITY BZCOMPONENT \"<<contentSpec.bzcomponent>>\">\n" +
												"<!ENTITY BZPRODUCT \"<<contentSpec.bzproduct>>\">";
	
	// VELOCITY TEMPLATE FILE NAMES
	public static String BOOK_INFO_TEMPLATE_NAME		= "Book_Info.xml.vm";
	public static String BOOK_ENT_TEMPLATE_NAME			= "Book.ent.vm";
	public static String BOOK_TEMPLATE_NAME				= "Book.xml.vm";
	public static String AUTHOR_GROUP_TEMPLATE_NAME		= "Author_Group.xml.vm";
	public static String REVISION_HISTORY_TEMPLATE_NAME	= "Revision_History.xml.vm";
	public static String PREFACE_TEMPLATE_NAME			= "Preface.xml.vm";
	public static String EMPTY_TOPIC_TEMPLATE_NAME		= "EmptyTopicError.xml.vm";
	public static String FAILED_VALIDATION_TOPIC_TEMPLATE_NAME	= "FailedValidationTopic.xml.vm";
	public static String FAILED_INJECTION_TOPIC_TEMPLATE_NAME	= "FailedInjectionTopic.xml.vm";
	public static String CHAPTER_TEMPLATE_NAME			= "ChapterTemplate.vm";
	public static String TOPIC_TEMPLATE_NAME			= "TopicTemplate.vm";
	public static String PREAMBLE_TEMPLATE_NAME			= "XMLWithPreambleTemplate.vm";
}
