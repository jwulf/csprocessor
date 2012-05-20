package com.redhat.contentspec.builder;

import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.tools.generic.DateTool;
import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.specimpl.PathSegmentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.redhat.contentspec.ContentSpec;
import com.redhat.contentspec.Level;
import com.redhat.contentspec.Part;
import com.redhat.contentspec.SpecTopic;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.builder.template.LevelXMLData;
import com.redhat.contentspec.builder.utils.DocbookUtils;
import com.redhat.contentspec.builder.utils.SAXXMLValidator;
import com.redhat.contentspec.builder.utils.XMLUtilities;
import com.redhat.contentspec.constants.CSConstants;
import com.redhat.contentspec.entities.AuthorInformation;
import com.redhat.contentspec.entities.InjectionOptions;
import com.redhat.contentspec.interfaces.ShutdownAbleApp;
import com.redhat.contentspec.rest.RESTManager;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import com.redhat.contentspec.structures.SpecDatabase;
import com.redhat.ecs.commonstructures.Pair;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.DocBookUtilities;
import com.redhat.ecs.commonutils.ExceptionUtilities;
import com.redhat.ecs.commonutils.StringUtilities;
import com.redhat.ecs.constants.CommonConstants;
import com.redhat.ecs.services.docbookcompiling.DocbookBuilderConstants;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorData;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorDatabase;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicImageData;
import com.redhat.topicindex.rest.collections.BaseRestCollectionV1;
import com.redhat.topicindex.rest.entities.BaseTopicV1;
import com.redhat.topicindex.rest.entities.BlobConstantV1;
import com.redhat.topicindex.rest.entities.ImageV1;
import com.redhat.topicindex.rest.entities.PropertyTagV1;
import com.redhat.topicindex.rest.entities.TagV1;
import com.redhat.topicindex.rest.entities.TopicV1;
import com.redhat.topicindex.rest.entities.TranslatedTopicV1;
import com.redhat.topicindex.rest.entities.UserV1;
import com.redhat.topicindex.rest.exceptions.InternalProcessingException;
import com.redhat.topicindex.rest.exceptions.InvalidParameterException;

public class DocbookBuilder<T extends BaseTopicV1<T>> implements ShutdownAbleApp
{
	private static final Logger log = Logger.getLogger(DocbookBuilder.class);
	private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
	private static final String STARTS_WITH_INVALID_SEQUENCE_RE = "^(?<InvalidSeq>[^\\w\\d]+)(?<EverythingElse>.*)$";
	
	private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	
	private final List<String> verbatimElements;
	private final List<String> inlineElements;
	private final List<String> contentsInlineElements;
	
	private final RESTReader reader;
	private final RESTManager restManager;
	private final BlobConstantV1 rocbookdtd;
	private final String defaultLocale;
	
	private CSDocbookBuildingOptions docbookBuildingOptions;
	private InjectionOptions injectionOptions;
	private Date buildDate;
	
	private String escapedTitle;
	private String locale;
	
	private String BOOK_FOLDER;
	private String BOOK_LOCALE_FOLDER;
	private String BOOK_TOPICS_FOLDER;
	private String BOOK_IMAGES_FOLDER;
	private String BOOK_FILES_FOLDER;
	
	private final VelocityEngine engine;
	
	private final Template preambleTemplate;
	private Template topicTemplate;
	private Template topicEmptyTemplate;
	private Template topicInjectionTemplate;
	private Template topicValidationTemplate;
	private Template bookEntTemplate;
	
	private String resourcePath;
	/**
	 * Holds the compiler errors that form the Errors.xml file in the compiled
	 * docbook
	 */
	private TopicErrorDatabase<T> errorDatabase;;
	
	/**
	 * Holds the SpecTopics and their XML that exist within the content specification
	 */
	private SpecDatabase specDatabase;
	
	/**
	 * Holds information on file url locations, which will be downloaded and
	 * included in the docbook zip file
	 */
	private final ArrayList<TopicImageData<T>> imageLocations = new ArrayList<TopicImageData<T>>();
	
	public DocbookBuilder(final RESTManager restManager, final BlobConstantV1 rocbookDtd, final String defaultLocale) throws InvalidParameterException, InternalProcessingException
	{
		engine = new VelocityEngine();
		Properties p = new Properties();
		p.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		p.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		engine.init(p);
		
		preambleTemplate = engine.getTemplate("/templates/" + BuilderConstants.PREAMBLE_TEMPLATE_NAME);
		
		reader = restManager.getReader();
		this.restManager = restManager;
		this.rocbookdtd = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
	
		this.defaultLocale = defaultLocale;
		
		/*
		 * Get the XML formatting details. These are used to pretty-print
		 * the XML when it is converted into a String.
		 */
		final String verbatimElementsString = System.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.VERBATIM_XML_ELEMENTS : System.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_SYSTEM_PROPERTY);
		final String inlineElementsString = System.getProperty(CommonConstants.INLINE_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.INLINE_XML_ELEMENTS : System.getProperty(CommonConstants.INLINE_XML_ELEMENTS_SYSTEM_PROPERTY);;
		final String contentsInlineElementsString = System.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS : System.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_SYSTEM_PROPERTY);
		
		verbatimElements = CollectionUtilities.toArrayList(verbatimElementsString.split(","));
		inlineElements = CollectionUtilities.toArrayList(inlineElementsString.split(","));
		contentsInlineElements = CollectionUtilities.toArrayList(contentsInlineElementsString.split(","));
	}
	
	@Override
	public void shutdown() {
		isShuttingDown.set(true);
	}

	@Override
	public boolean isShutdown() {
		return shutdown.get();
	}
	
	public int getNumWarnings() {
		int numWarnings = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null) 
		{
			for (TopicErrorData<T> errorData: errorDatabase.getErrors(locale)) {
				numWarnings += errorData.getItemsOfType(TopicErrorDatabase.WARNING).size();
			}
		}
		return numWarnings;
	}

	public int getNumErrors() {
		int numErrors = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null) 
		{
			for (TopicErrorData<T> errorData: errorDatabase.getErrors(locale)) {
				numErrors += errorData.getItemsOfType(TopicErrorDatabase.ERROR).size();
			}
		}
		return numErrors;
	}
	
	public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserV1 requester, final CSDocbookBuildingOptions buildingOptions, final String searchTagsUrl) throws Exception
	{
		if (contentSpec == null) throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
		
		errorDatabase = new TopicErrorDatabase<T>();
		specDatabase = new SpecDatabase();
		
		/* Get the output style */
		String outputStylePath = StringUtilities.buildString(contentSpec.getOutputStyle().split("-"), "/");
		
		if (outputStylePath.equals(CSConstants.SKYNET_OUTPUT_FORMAT) || outputStylePath.equals(CSConstants.CSP_OUTOUT_FORMAT))
		{
			outputStylePath += "/default";
		}
		
		/* Set the resource path of the templates to be used */
		resourcePath = "/templates/" + outputStylePath + "/";
		topicTemplate = engine.getTemplate(resourcePath + BuilderConstants.TOPIC_TEMPLATE_NAME);
		topicEmptyTemplate = engine.getTemplate(resourcePath + BuilderConstants.EMPTY_TOPIC_TEMPLATE_NAME);
		topicInjectionTemplate = engine.getTemplate(resourcePath + BuilderConstants.FAILED_INJECTION_TOPIC_TEMPLATE_NAME);
		topicValidationTemplate = engine.getTemplate(resourcePath + BuilderConstants.FAILED_VALIDATION_TOPIC_TEMPLATE_NAME);
		bookEntTemplate = engine.getTemplate(resourcePath + BuilderConstants.BOOK_ENT_TEMPLATE_NAME);
		
		// Setup the constants
		escapedTitle = DocBookUtilities.escapeTitle(contentSpec.getTitle());
		locale = contentSpec.getLocale() == null ? defaultLocale : contentSpec.getLocale();
		BOOK_FOLDER = escapedTitle + "/";
		BOOK_LOCALE_FOLDER = BOOK_FOLDER + locale + "/";
		BOOK_TOPICS_FOLDER = BOOK_LOCALE_FOLDER + "topics/";
		BOOK_IMAGES_FOLDER = BOOK_LOCALE_FOLDER + "images/";
		BOOK_FILES_FOLDER = BOOK_LOCALE_FOLDER + "files/";
		buildDate = new Date();
		
		this.docbookBuildingOptions = buildingOptions;
		
		// Add the options that were passed to the builder
		injectionOptions = new InjectionOptions();
		
		// Get the injection mode
		InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
		Boolean injection = buildingOptions.getInjection();
		if (injection != null && !injection) injectionType = InjectionOptions.UserType.OFF;
		else if (injection != null && injection) injectionType = InjectionOptions.UserType.ON;
		
		// Add the strict injection types
		if (buildingOptions.getInjectionTypes() != null) {
			for (final String injectType : buildingOptions.getInjectionTypes()) {
				injectionOptions.addStrictTopicType(injectType.trim());
			}
			if (injection != null && injection) {
				injectionType = InjectionOptions.UserType.STRICT;
			}
		}
		
		// Set the injection mode
		injectionOptions.setClientType(injectionType);
		
		// Set the injection options for the content spec
		if (contentSpec.getInjectionOptions() != null) {
			injectionOptions.setContentSpecType(contentSpec.getInjectionOptions().getContentSpecType());
			injectionOptions.addStrictTopicTypes(contentSpec.getInjectionOptions().getStrictTopicTypes());
		}
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		final Map<Integer, Set<String>> usedIdAttributes = new HashMap<Integer, Set<String>>();
		final boolean fixedUrlsSuccess = doPopulateDatabasePass(contentSpec, usedIdAttributes);
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		// second topic pass to set the ids and process injections
		doSpecTopicPass(contentSpec, searchTagsUrl, usedIdAttributes, fixedUrlsSuccess, BuilderConstants.BUILD_NAME);
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		/* Process the images in the topics */
		processImageLocations();
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		return doBuildZipPass(contentSpec, requester, fixedUrlsSuccess);
	}
	
	/**
	 * Populates the SpecTopicDatabase with the SpecTopics inside the content specification.
	 * It also adds the equivalent real topics to each SpecTopic.
	 * 
	 * @param contentSpec The content spec to populate the database from
	 */
	@SuppressWarnings("unchecked")
	private boolean doPopulateDatabasePass(final ContentSpec contentSpec, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " Populate Database Pass");
		
		/* Calculate the ids of all the topics to get */
		final Set<Integer> topicIds = getTopicIdsFromLevel(contentSpec.getBaseLevel());
		
		final BaseRestCollectionV1<T> topics;
		final boolean fixedUrlsSuccess;
		if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(defaultLocale))
		{
			final BaseRestCollectionV1<TopicV1> normalTopics = reader.getTopicsByIds(CollectionUtilities.toArrayList(topicIds));
			
			/*
			 * assign fixed urls property tags to the topics. If
			 * fixedUrlsSuccess is true, the id of the topic sections,
			 * xfref injection points and file names in the zip file
			 * will be taken from the fixed url property tag, defaulting
			 * back to the TopicID## format if for some reason that
			 * property tag does not exist.
			 */
			fixedUrlsSuccess = setFixedURLsPass(normalTopics);
			
			topics = (BaseRestCollectionV1<T>) normalTopics;
		}
		else
		{
			final BaseRestCollectionV1<TranslatedTopicV1> translatedTopics = reader.getTranslatedTopicsByTopicIds(CollectionUtilities.toArrayList(topicIds), contentSpec.getLocale());
			List<Integer> dummyTopicIds = new ArrayList<Integer>(topicIds);
			
			/* Remove any topic ids for translated topics that were found */
			if (translatedTopics != null && translatedTopics.getItems() != null)
			{
				for (final TranslatedTopicV1 topic : translatedTopics.getItems())
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return false;
					}
					
					dummyTopicIds.remove(topic.getTopicId());
				}
			}
			
			/* Create the dummy translated topics */
			populateDummyTranslatedTopicsPass(translatedTopics, dummyTopicIds);
			
			/* 
			 * Translations should reference an existing historical topic with
			 * the fixed urls set, so we assume this to be the case
			 */
			fixedUrlsSuccess = true;
			
			/* set the topics variable now all initialisation is done */
			topics = (BaseRestCollectionV1<T>) translatedTopics;
		}
		
		/* Add all the levels and topics to the database first */
		addLevelAndTopicsToDatabase(contentSpec.getBaseLevel(), fixedUrlsSuccess);
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return false;
		}
		
		/* Pass the topics to make sure they are valid */
		doTopicPass(topics, fixedUrlsSuccess, usedIdAttributes);
		
		return fixedUrlsSuccess;
	}
	
	private void addLevelAndTopicsToDatabase(final Level level, final boolean useFixedUrls)
	{
		/* Add the level to the database */
		specDatabase.add(level, createURLTitle(level.getTitle()));
		
		/* Add the topics at this level to the database */
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			specDatabase.add(specTopic, specTopic.getUniqueLinkId(useFixedUrls));
		}
		
		/* Add the child levels to the database */
		
		for (final Level childLevel : level.getChildLevels())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			addLevelAndTopicsToDatabase(childLevel, useFixedUrls);
		}
	}
	
	private Set<Integer> getTopicIdsFromLevel(final Level level)
	{
		/* Add the topics at this level to the database */
		final Set<Integer> topicIds = new HashSet<Integer>();
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return topicIds;
			}
			
			if (specTopic.getDBId() != 0)
				topicIds.add(specTopic.getDBId());
		}
		
		/* Add the child levels to the database */
		
		for (final Level childLevel : level.getChildLevels())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return topicIds;
			}
			
			topicIds.addAll(getTopicIdsFromLevel(childLevel));
		}
		
		return topicIds;
	}
	
	/**
	 * Populates the topics with a set of dummy topics as specified by the dummyTopicIds list.
	 * 
	 * @param topics The set of topics to add the dummy translated topics to.
	 * @param dummyTopicIds The list of topics to be added as dummy translated topics.
	 */
	private void populateDummyTranslatedTopicsPass(final BaseRestCollectionV1<TranslatedTopicV1> topics, final List<Integer> dummyTopicIds) 
	{
		log.info("\tDoing dummy Translated Topic pass");
		
		final BaseRestCollectionV1<TopicV1> dummyTopics = reader.getTopicsByIds(dummyTopicIds);
		
		/* Only continue if we found dummy topics */
		if (dummyTopics == null || dummyTopics.getItems() == null || dummyTopics.getItems().isEmpty()) return;
		
		/* Split the topics up into their different locales */
		final Map<String, Map<Integer, TranslatedTopicV1>> groupedLocaleTopics = new HashMap<String, Map<Integer, TranslatedTopicV1>>();
		
		if (topics != null && topics.getItems() != null)
		{
			for (final TranslatedTopicV1 topic: topics.getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				if (!groupedLocaleTopics.containsKey(topic.getLocale()))
					groupedLocaleTopics.put(topic.getLocale(), new HashMap<Integer, TranslatedTopicV1>());
				groupedLocaleTopics.get(topic.getLocale()).put(topic.getTopicId(), topic);
			}
		}
		
		/* create and add the dummy topics per locale */
		for (final String locale : groupedLocaleTopics.keySet())
		{
			final Map<Integer, TranslatedTopicV1> translatedTopicsMap = groupedLocaleTopics.get(locale);
			for (final TopicV1 topic: dummyTopics.getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				if (!translatedTopicsMap.containsKey(topic.getId()))
				{
					final TranslatedTopicV1 dummyTopic = createDummyTranslatedTopic(translatedTopicsMap, topic, true, locale);
					
					topics.addItem(dummyTopic);
				}
			}
		}
	}
	
	/**
	 * Creates a dummy translated topic so that a book
	 * can be built using the same relationships as a 
	 * normal build.
	 * 
	 * @param translatedTopicsMap A map of topic ids to translated topics
	 * @param topic The topic to create the dummy topic from
	 * @param expandRelationships Whether the relationships should be expanded for the dummy topic
	 * @return The dummy translated topic
	 */
	private TranslatedTopicV1 createDummyTranslatedTopic(final Map<Integer, TranslatedTopicV1> translatedTopicsMap, final TopicV1 topic, final boolean expandRelationships, final String locale)
	{	
		final TranslatedTopicV1 translatedTopic = new TranslatedTopicV1();
		
		translatedTopic.setId(topic.getId() * -1);
		translatedTopic.setTopicId(topic.getId());
		translatedTopic.setTopicRevision(topic.getRevision().intValue());
		translatedTopic.setTopic(topic);
		translatedTopic.setTranslationPercentage(100);
		translatedTopic.setRevision(topic.getRevision());
		translatedTopic.setXml(topic.getXml());
		translatedTopic.setTags(topic.getTags());
		translatedTopic.setSourceUrls_OTM(topic.getSourceUrls_OTM());
		translatedTopic.setProperties(topic.getProperties());
		translatedTopic.setLocale(locale);
		
		/* prefix the locale to show that it is missing the related translated topic */
		translatedTopic.setTitle("[" + topic.getLocale() + "] " + topic.getTitle());
		
		/* Add the dummy outgoing relationships */
		if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null)
		{
			final BaseRestCollectionV1<TranslatedTopicV1> outgoingRelationships = new BaseRestCollectionV1<TranslatedTopicV1>();
			for (final TopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return translatedTopic;
				}
				
				/* check to see if the translated topic already exists */
				if (translatedTopicsMap.containsKey(relatedTopic.getId()))
				{
					outgoingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
				}
				else
				{
					outgoingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, false, locale));
				}
			}
			translatedTopic.setOutgoingRelationships(outgoingRelationships);
		}
		
		/* Add the dummy incoming relationships */
		if (topic.getIncomingRelationships() != null && topic.getIncomingRelationships().getItems() != null)
		{
			final BaseRestCollectionV1<TranslatedTopicV1> incomingRelationships = new BaseRestCollectionV1<TranslatedTopicV1>();
			for (final TopicV1 relatedTopic : topic.getIncomingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return translatedTopic;
				}
				
				/* check to see if the translated topic already exists */
				if (translatedTopicsMap.containsKey(relatedTopic.getId()))
				{
					incomingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
				}
				else
				{
					incomingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, false, locale));
				}
			}
			translatedTopic.setIncomingRelationships(incomingRelationships);
		}
		
		return translatedTopic;
	}
	
	private void doTopicPass(final BaseRestCollectionV1<T> topics, final boolean fixedUrlsSuccess, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " First topic pass");
		log.info("\tProcessing " + topics.getItems().size() + " Topics");
		
		/* Check that we have some topics to process */
		if (topics != null && topics.getItems() != null)
		{
			
			final int showPercent = 5;
			final float total = topics.getItems().size();
			float current = 0;
			int lastPercent = 0;
			
			/* Process each topic */
			for (final T topic : topics.getItems())
			{
				++current;
				final int percent = Math.round(current / total * 100);
				if (percent - lastPercent >= showPercent)
				{
					lastPercent = percent;
					log.info("\tFirst topic Pass " + percent + "% Done");
				}
				
				/* Set up the topic ctx for the templates */
				VelocityContext topicCtx = new VelocityContext();
				topicCtx.put("title", topic.getTitle());
				topicCtx.put("topicSectionId", fixedUrlsSuccess ? topic.getXrefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID) : topic.getXRefID());
				
				/* Find the Topic ID */
				final Integer topicId;
				if (topic instanceof TranslatedTopicV1)
				{
					topicId = ((TranslatedTopicV1) topic).getTopicId();
					topicCtx.put("topicId", topicId);
					topicCtx.put("topicRevision", ((TranslatedTopicV1) topic).getTopicRevision());
				}
				else
				{
					topicId = topic.getId();
					topicCtx.put("topicId", topicId);
				}
				
				final String topicXML = topic == null ? null : topic.getXml();
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				boolean xmlValid = true;
				
				Document topicDoc = null;
				
				// Check that the Topic XML exists and isn't empty
				if (topicXML == null || topicXML.equals(""))
				{
					// Create an empty topic with the topic title from the resource file
					final StringWriter emptyTopicWriter = new StringWriter();
					topicEmptyTemplate.merge(new VelocityContext(), emptyTopicWriter);
					topicCtx.put("content", emptyTopicWriter.toString());
					
					errorDatabase.addWarning(topic, BuilderConstants.EMPTY_TOPIC_XML);
					topicDoc = null;
					xmlValid = false;
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				/* make sure we have valid XML */
				if (xmlValid)
				{
					topicDoc = XMLUtilities.convertStringToDocument(topic.getXml());
					if (topicDoc == null)
					{
						topicCtx.put("showErrorsPage", !docbookBuildingOptions.getSuppressErrorsPage());
						topicCtx.put("topicErrorXRef", topic.getErrorXRefID());
						
						final StringWriter failedValidationTopicWriter = new StringWriter();
						topicValidationTemplate.merge(topicCtx, failedValidationTopicWriter);
						topicCtx.put("content", failedValidationTopicWriter.toString());
						
						errorDatabase.addError(topic, BuilderConstants.BAD_XML_STRUCTURE);
					}
				}
				
				final Document doc = XMLUtilities.convertStringToDocument(mergeTopicContextWithDocument(topicDoc, topicCtx));
				
				/*
				 * Extract the id attributes used in this topic. We'll use this data
				 * in the second pass to make sure that individual topics don't
				 * repeat id attributes.
				 */
				collectIdAttributes(topicId, doc, usedIdAttributes);
				
				/* Add the document & topic to the database spec topics */
				List<SpecTopic> specTopics = specDatabase.getSpecTopicsForTopicID(topicId);
				for (final SpecTopic specTopic : specTopics)
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return;
					}
					
					specTopic.setTopic(topic.clone(false));
					specTopic.setXmlDocument(doc);
					specDatabase.setSpecTopicContext(specTopic, (VelocityContext) topicCtx.clone());
				}
	
			}
		}
	}
	
	/**
	 * Loops through each of the spec topics in the database and sets the injections and unique ids for
	 * each id attribute in the Topics XML.
	 * 
	 * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
	 * @param fixedUrlsSuccess If during processing the fixed urls should be used.
	 */
	@SuppressWarnings("unchecked")
	private void doSpecTopicPass(final ContentSpec contentSpec, final String searchTagsUrl, final Map<Integer, Set<String>> usedIdAttributes, final boolean fixedUrlsSuccess, final String buildName)
	{	
		log.info("Doing " + locale + " Spec Topic Pass");
		log.info("\tProcessing " + specDatabase.getAllSpecTopics().size() + " Spec Topics");
		
		final int showPercent = 5;
		final float total = specDatabase.getAllSpecTopics().size();
		float current = 0;
		int lastPercent = 0;
		
		for (final SpecTopic specTopic : specDatabase.getAllSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			++current;
			final int percent = Math.round(current / total * 100);
			if (percent - lastPercent >= showPercent)
			{
				lastPercent = percent;
				log.info("\tProcessing Pass " + percent + "% Done");
			}
			
			final T topic = (T) specTopic.getTopic();
			final Document doc = specTopic.getXmlDocument();
			final VelocityContext topicCtx = specDatabase.getSpecTopicContext(specTopic);
			final Level baseLevel = contentSpec.getBaseLevel();
			
			topicCtx.put("topicSectionId", specTopic.getUniqueLinkId(fixedUrlsSuccess));
			
			final XMLPreProcessor<T> xmlPreProcessor = new XMLPreProcessor<T>();
			boolean valid = true;
			
			if (doc != null && topicCtx != null)
			{
				/* process the injection points */
				if (injectionOptions.isInjectionAllowed())
				{
	
					final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();
					final List<Integer> genericInjectionErrors;
					
					final List<Integer> customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, docbookBuildingOptions, fixedUrlsSuccess);
					
					if (contentSpec.getOutputStyle().startsWith(CSConstants.SKYNET_OUTPUT_FORMAT))
					{
						/*
						 * create a collection of the tags that make up the topics types
						 * that will be included in generic injection points
						 */
						final List<Pair<Integer, String>> topicTypeTagDetails = new ArrayList<Pair<Integer, String>>();
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.TASK_TAG_ID, DocbookBuilderConstants.TASK_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.REFERENCE_TAG_ID, DocbookBuilderConstants.REFERENCE_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPT_TAG_ID, DocbookBuilderConstants.CONCEPT_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_ID, DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_NAME));
						
						genericInjectionErrors = xmlPreProcessor.processGenericInjections(baseLevel, specTopic, topicCtx, customInjectionIds, topicTypeTagDetails, docbookBuildingOptions, fixedUrlsSuccess);
					}
					else
					{
						xmlPreProcessor.processPrerequisiteInjections(specTopic, topicCtx, fixedUrlsSuccess);
						xmlPreProcessor.processPrevRelationshipInjections(specTopic, topicCtx, fixedUrlsSuccess);
						xmlPreProcessor.processNextRelationshipInjections(specTopic, topicCtx, fixedUrlsSuccess);
						xmlPreProcessor.processSeeAlsoInjections(specTopic, topicCtx, fixedUrlsSuccess);
						
						genericInjectionErrors = new ArrayList<Integer>();
					}
										
					final List<Integer> topicContentFragmentsErrors = xmlPreProcessor.processTopicContentFragments(specTopic, doc, docbookBuildingOptions);
					final List<Integer> topicTitleFragmentsErrors = xmlPreProcessor.processTopicTitleFragments(specTopic, doc, docbookBuildingOptions);
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return;
					}
	
					if (!customInjectionErrors.isEmpty())
					{
						final String message = "Topic has referenced Topic(s) " + CollectionUtilities.toSeperatedString(customInjectionErrors) + " in a custom injection point that was either not related, or not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
					

					if (!genericInjectionErrors.isEmpty())
					{
						final String message = "Topic has related Topic(s) " + CollectionUtilities.toSeperatedString(CollectionUtilities.toAbsIntegerList(genericInjectionErrors)) + " that were not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
	
					if (!topicContentFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected content from Topic(s) " + CollectionUtilities.toSeperatedString(topicContentFragmentsErrors) + " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
	
					if (!topicTitleFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected a title from Topic(s) " + CollectionUtilities.toSeperatedString(topicTitleFragmentsErrors) + " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
					
					/* check for dummy topics */
					if (topic instanceof TranslatedTopicV1)
					{
						/* Add the warning for the topics relationships that haven't been translated */
						if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null)
						{
							for (T relatedTopic: topic.getOutgoingRelationships().getItems())
							{
								// Check if the app should be shutdown
								if (isShuttingDown.get()) {
									return;
								}
								
								final TranslatedTopicV1 relatedTranslatedTopic = (TranslatedTopicV1) relatedTopic;
								
								/* Only show errors for topics that weren't included in the injections */
								if (!customInjectionErrors.contains(relatedTranslatedTopic.getTopicId()) && !genericInjectionErrors.contains(relatedTopic.getId()))
								{
									if ((!baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()) && !docbookBuildingOptions.getIgnoreMissingCustomInjections()) || baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()))
									{
										if (relatedTopic.isDummyTopic() && relatedTranslatedTopic.hasBeenPushedForTranslation())
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" is an untranslated topic.");
										else if (relatedTopic.isDummyTopic())
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" hasn't been pushed for translation.");
									}
								}
							}
						}
						
						/* Check the topic itself isn't a dummy topic */
						if (topic.isDummyTopic() && ((TranslatedTopicV1) topic).hasBeenPushedForTranslation())
							errorDatabase.addWarning(topic, "This topic is an untranslated topic.");
						else if (topic.isDummyTopic())
							errorDatabase.addWarning(topic, "This topic hasn't been pushed for translation.");
					}
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}

				if (!valid)
				{
					final Document processedDoc = XMLUtilities.convertStringToDocument(mergeTopicContextWithDocument(doc, topicCtx));
					
					topicCtx.put("showErrorsPage", !docbookBuildingOptions.getSuppressErrorsPage());
					topicCtx.put("topicErrorXRef", topic.getErrorXRefID());
					
					final StringWriter failedInjectionTopicWriter = new StringWriter();
					topicInjectionTemplate.merge(topicCtx, failedInjectionTopicWriter);
					topicCtx.put("content", failedInjectionTopicWriter.toString());
					
					final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(processedDoc, verbatimElements, inlineElements, contentsInlineElements, true));
					errorDatabase.addError(topic, "Topic has invalid Injection Points. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
				}
				else
				{
					/* add the standard boilerplate xml */
					xmlPreProcessor.processTopicAdditionalInfo(specTopic, topicCtx, docbookBuildingOptions, buildName, searchTagsUrl, buildDate);
					
					/* Convert the current template to a document */
					final Document processedDoc = XMLUtilities.convertStringToDocument(mergeTopicContextWithDocument(doc, topicCtx));
					
					/*
					 * make sure the XML is valid docbook after the standard
					 * processing has been done
					 */
					final SAXXMLValidator validator = new SAXXMLValidator();
					if (!validator.validateXML(processedDoc, BuilderConstants.ROCBOOK_45_DTD, rocbookdtd.getValue()))
					{
						topicCtx.put("showErrorsPage", !docbookBuildingOptions.getSuppressErrorsPage());
						topicCtx.put("topicErrorXRef", topic.getErrorXRefID());
						
						final StringWriter failedValidationTopicWriter = new StringWriter();
						topicValidationTemplate.merge(topicCtx, failedValidationTopicWriter);
						topicCtx.put("content", failedValidationTopicWriter.toString());
						
						final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(processedDoc, verbatimElements, inlineElements, contentsInlineElements, true));
						errorDatabase.addError(topic, "Topic has invalid Docbook XML. The error is <emphasis>" + validator.getErrorText() + "</emphasis>. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
					}
				}
				
				/* 
				 * Ensure that all of the id attributes are valid
				 * by setting any duplicates with a post fixed number.
				 */
				setUniqueIds(specTopic, doc, usedIdAttributes);
			}
		}
	}
	
	/**
	 * Sets the "id" attributes in the supplied XML node so that they will be
	 * unique within the book.
	 * 
	 * @param specTopic The topic the node belongs to.
	 * @param node The node to process for id attributes.
	 * @param usedIdAttributes The list of usedIdAttributes.
	 */
	private void setUniqueIds(final SpecTopic specTopic, final Node node, final Map<Integer, Set<String>> usedIdAttributes)
	{	
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node idAttribute = attributes.getNamedItem("id");
			if (idAttribute != null)
			{
				String idAttributeValue = idAttribute.getNodeValue();
				
				if (specTopic.getDuplicateId() != null)
					idAttributeValue += "-" + specTopic.getDuplicateId();
				
				if (!isUniqueAttributeId(idAttributeValue, specTopic.getDBId(), usedIdAttributes))
					idAttributeValue += "-" + specTopic.getStep();
				
				setUniqueIdReferences(node, idAttribute.getNodeValue(), idAttributeValue);
				
				idAttribute.setNodeValue(idAttributeValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
			setUniqueIds(specTopic, elements.item(i), usedIdAttributes);
	}
	
	/**
	 * ID attributes modified in the setUniqueIds() method may have been referenced
	 * locally in the XML. When an ID is updated, and attribute that referenced
	 * that ID is also updated.
	 * 
	 * @param node
	 *            The node to check for attributes
	 * @param id
	 *            The old ID attribute value
	 * @param fixedId
	 *            The new ID attribute
	 */
	private void setUniqueIdReferences(final Node node, final String id, final String fixedId)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			for (int i = 0; i < attributes.getLength(); ++i)
			{
				final String attibuteValue = attributes.item(i).getNodeValue();
				if (attibuteValue.equals(id))
				{
					attributes.item(i).setNodeValue(fixedId);
				}
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
			setUniqueIdReferences(elements.item(i), id, fixedId);
	}
	
	/**
	 * Checks to see if a supplied attribute id is unique within this book, based
	 * upon the used id attributes that were calculated earlier.
	 * 
	 * @param id The Attribute id to be checked
	 * @param topicId The id of the topic the attribute id was found in
	 * @param usedIdAttributes The set of used ids calculated earlier
	 * @return True if the id is unique otherwise false.
	 */
	private boolean isUniqueAttributeId(final String id, final Integer topicId, final Map<Integer, Set<String>> usedIdAttributes)
	{
		boolean retValue = true;

		if (usedIdAttributes.containsKey(topicId))
		{
			final Set<String> ids1 = usedIdAttributes.get(topicId);

			for (final Integer topicId2 : usedIdAttributes.keySet())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return false;
				}
				
				if (topicId2.equals(topicId))
					continue;

				if (usedIdAttributes.containsKey(topicId2))
				{
					final Set<String> ids2 = usedIdAttributes.get(topicId2);

					for (final String id1 : ids1)
					{
						// Check if the app should be shutdown
						if (isShuttingDown.get()) {
							return false;
						}
						
						if (ids2.contains(id1))
						{
							retValue = false;
						}
					}
				}
			}
		}

		return retValue;
	}
	
	/**
	 * This function scans the supplied XML node and it's children for id
	 * attributes, collecting them in the usedIdAttributes parameter
	 * 
	 * @param node
	 *            The current node being processed (will be the document root to
	 *            start with, and then all the children as this function is
	 *            recursively called)
	 */
	private void collectIdAttributes(final Integer topicId, final Node node, final Map<Integer, Set<String>> usedIdAttributes)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node idAttribute = attributes.getNamedItem("id");
			if (idAttribute != null)
			{
				final String idAttibuteValue = idAttribute.getNodeValue();
				if (!usedIdAttributes.containsKey(topicId))
					usedIdAttributes.put(topicId, new HashSet<String>());
				usedIdAttributes.get(topicId).add(idAttibuteValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
			collectIdAttributes(topicId, elements.item(i), usedIdAttributes);
	}
	
	private HashMap<String, byte[]> doBuildZipPass(final ContentSpec contentSpec, final UserV1 requester, final boolean fixedUrlsSuccess) throws InvalidParameterException, InternalProcessingException
	{
		log.info("Building the ZIP file");
		
		final List<LevelXMLData> levels = new LinkedList<LevelXMLData>();
		
		/* Add the base book information */
		final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
		final VelocityContext bookCtx = buildBookBase(contentSpec, requester, files);
		
		/* add the images to the book */
		addImagesToBook(files);

		// Loop through and create each chapter and the topics inside those chapters
		final LinkedList<com.redhat.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();
		for (com.redhat.contentspec.Node node: levelData) {
		
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return null;
			}
			
			if (node instanceof Part) {
				Part part = (Part)node;
				
				final LevelXMLData level = new LevelXMLData("part", part.getTitle(), part.getUniqueLinkId(fixedUrlsSuccess));
				levels.add(level);
				
				for (Level childLevel: part.getChildLevels())
				{
					if (childLevel.hasSpecTopics())
					{
						createChapterXML(files, childLevel, fixedUrlsSuccess);
						level.addChildLevel(new LevelXMLData(childLevel.getUniqueLinkId(fixedUrlsSuccess)));
					}
				}
			} else if (node instanceof Level) {
				final Level level = (Level) node;
				
				if (level.hasSpecTopics())
				{
					createChapterXML(files, level, fixedUrlsSuccess);
					levels.add(new LevelXMLData(level.getUniqueLinkId(fixedUrlsSuccess)));
				}
			}
		}
		
		
		/* add any compiler errors */
		if (!docbookBuildingOptions.getSuppressErrorsPage())
		{
			final String compilerOutput = buildErrorChapter(locale);
			files.put(BOOK_LOCALE_FOLDER + "Errors.xml", StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
			levels.add(new LevelXMLData("Errors.xml"));
		}
		
		/* build the content specification page */
		if (!docbookBuildingOptions.getSuppressContentSpecPage())
		{
			files.put(BOOK_LOCALE_FOLDER + "Build_Content_Specification.xml", DocbookUtils.buildAppendix(DocbookUtils.wrapInPara("<programlisting>" + XMLUtilities.wrapStringInCDATA(contentSpec.toString()) + "</programlisting>"), "Build Content Specification").getBytes());
			levels.add(new LevelXMLData("appe-Build_Content_Specification.xml.xml"));
		}
		
		bookCtx.put("levels", levels);
		
		final StringWriter bookWriter = new StringWriter();
		preambleTemplate.merge(bookCtx, bookWriter);

		files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".xml", bookWriter.toString().getBytes());
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return null;
		}
		
		return files;
	}
	
	/**
	 * Builds the basics of a Docbook from the resource files for a specific content specification.
	 * 
	 * @param contentSpec The content specification object to be built.
	 * @param vairables A mapping of variables that are used as override parameters
	 * @param requester The User who requested the book be built
	 * @return A Document object to be used in generating the book.xml
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	private VelocityContext buildBookBase(final ContentSpec contentSpec, final UserV1 requester, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tAdding standard files to Publican ZIP file");
		
		final Map<String, String> variables = docbookBuildingOptions.getOverrides();
		
		final String publicanCfg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.PUBLICAN_CFG_ID, "").getValue();
		
		// Setup publican.cfg
		final String brand = contentSpec.getBrand() == null ? "common" : contentSpec.getBrand();
		String fixedPublicanCfg = publicanCfg.replaceAll(BuilderConstants.BRAND_REGEX, brand);
		fixedPublicanCfg = fixedPublicanCfg.replaceFirst("xml_lang\\: .*(\\r\\n|\\n)", "xml_lang: " + locale + "\n");
		if (contentSpec.getPublicanCfg() != null) {
			fixedPublicanCfg += contentSpec.getPublicanCfg();
		}
		
		if (docbookBuildingOptions.getPublicanShowRemarks())
		{
			fixedPublicanCfg += "\nshow_remarks: 1";
		}

		if (docbookBuildingOptions.getCvsPkgOption() != null)
		{
			fixedPublicanCfg += "\ncvs_pkg: " + docbookBuildingOptions.getCvsPkgOption();
		}
		
		files.put(BOOK_FOLDER + "publican.cfg", fixedPublicanCfg.getBytes());
		
		// Setup Book_Info.xml
		final VelocityContext bookInfoCtx = new VelocityContext();
		bookInfoCtx.put("contentSpec", contentSpec);
		bookInfoCtx.put("overrides", variables);
		bookInfoCtx.put("templateFile", resourcePath + BuilderConstants.BOOK_INFO_TEMPLATE_NAME);
		bookInfoCtx.put("escapedBookTitle", escapedTitle);
		
		final StringWriter bookInfoWriter = new StringWriter();
		preambleTemplate.merge(bookInfoCtx, bookInfoWriter);
		
		files.put(BOOK_LOCALE_FOLDER + "Book_Info.xml", bookInfoWriter.toString().getBytes());
		
		// Setup Author_Group.xml
		buildAuthorGroup(contentSpec, files);
		
		// Setup Preface.xml
		final VelocityContext prefaceCtx = new VelocityContext();
		prefaceCtx.put("contentSpec", contentSpec);
		prefaceCtx.put("templateFile", resourcePath + BuilderConstants.PREFACE_TEMPLATE_NAME);
		prefaceCtx.put("escapedBookTitle", escapedTitle);
		
		final StringWriter prefaceWriter = new StringWriter();
		preambleTemplate.merge(prefaceCtx, prefaceWriter);
		
		files.put(BOOK_LOCALE_FOLDER + "Preface.xml", prefaceWriter.toString().getBytes());
		
		// Setup Revision_History.xml
		buildRevisionHistory(contentSpec, requester, files);
		
		// Setup the <<contentSpec.title>>.ent file
		final VelocityContext bookEntCtx = new VelocityContext();
		bookEntCtx.put("contentSpec", contentSpec);
		bookEntCtx.put("date", new DateTool());
		
		final StringWriter bookEntWriter = new StringWriter();
		bookEntTemplate.merge(bookEntCtx, bookEntWriter);
		
		files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".ent", bookEntWriter.toString().getBytes());
		
		// Setup the images and files folders
		final String iconSvg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.ICON_SVG_ID, "").getValue();
		files.put(BOOK_IMAGES_FOLDER + "icon.svg", iconSvg.getBytes());
		
		if (contentSpec.getOutputStyle() != null && contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			final String jbossSvg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.JBOSS_SVG_ID, "").getValue();

			final String yahooDomEventJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.YAHOO_DOM_EVENT_JS_ID, "").getValue();
			final String treeviewMinJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.TREEVIEW_MIN_JS_ID, "").getValue();
			final String treeviewCss = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.TREEVIEW_CSS_ID, "").getValue();
			final String jqueryMinJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.JQUERY_MIN_JS_ID, "").getValue();

			final byte[] treeviewSpriteGif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.TREEVIEW_SPRITE_GIF_ID, "").getValue();
			final byte[] treeviewLoadingGif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.TREEVIEW_LOADING_GIF_ID, "").getValue();
			final byte[] check1Gif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.CHECK1_GIF_ID, "").getValue();
			final byte[] check2Gif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.CHECK2_GIF_ID, "").getValue();
			
			// these files are used by the YUI treeview
			files.put(BOOK_FILES_FOLDER + "yahoo-dom-event.js", StringUtilities.getStringBytes(yahooDomEventJs));
			files.put(BOOK_FILES_FOLDER + "treeview-min.js", StringUtilities.getStringBytes(treeviewMinJs));
			files.put(BOOK_FILES_FOLDER + "treeview.css", StringUtilities.getStringBytes(treeviewCss));
			files.put(BOOK_FILES_FOLDER + "jquery.min.js", StringUtilities.getStringBytes(jqueryMinJs));
	
			// these are the images that are referenced in the treeview.css file
			files.put(BOOK_FILES_FOLDER + "treeview-sprite.gif", treeviewSpriteGif);
			files.put(BOOK_FILES_FOLDER + "treeview-loading.gif", treeviewLoadingGif);
			files.put(BOOK_FILES_FOLDER + "check1.gif", check1Gif);
			files.put(BOOK_FILES_FOLDER + "check2.gif", check2Gif);
	
			files.put(BOOK_IMAGES_FOLDER + "jboss.svg", StringUtilities.getStringBytes(jbossSvg));
		}
		
		// Setup the basic book.xml
		final VelocityContext bookCtx = new VelocityContext();
		bookCtx.put("contentSpec", contentSpec);
		bookCtx.put("escapedBookTitle", escapedTitle);
		bookCtx.put("templateFile", resourcePath + BuilderConstants.BOOK_TEMPLATE_NAME);
		
		return bookCtx;
	}
	
	/**
	 * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
	 * 
	 * @param level The level to build the chapter from.
	 */
	protected void createChapterXML(final Map<String, byte[]> files, final Level level, final boolean fixedUrlsSuccess) {
			
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		// Create the title
		final String chapterName = level.getUniqueLinkId(fixedUrlsSuccess) + ".xml";
		final List<LevelXMLData> levels = new LinkedList<LevelXMLData>();
		final VelocityContext chapterCtx = new VelocityContext();
		chapterCtx.put("chapterId", level.getUniqueLinkId(fixedUrlsSuccess));
		chapterCtx.put("templateFile", resourcePath + BuilderConstants.CHAPTER_TEMPLATE_NAME);
		chapterCtx.put("title", level.getTitle());
		chapterCtx.put("escapedBookTitle", escapedTitle);
		
		createSectionXML(files, level, levels, fixedUrlsSuccess);
		
		chapterCtx.put("levels", levels);
		
		final StringWriter chapterWriter = new StringWriter();
		preambleTemplate.merge(chapterCtx, chapterWriter);
		
		// Add the chapter to the book
		files.put(BOOK_LOCALE_FOLDER + chapterName, chapterWriter.toString().getBytes());
	}
	
	/**
	 * Creates the section component of a chapter.xml for a specific ContentLevel.
	 * 
	 * @param levelData A map containing the data for this Section's level ordered by a step.
	 * @param chapter The chapter document object that this section is to be added to.
	 * @param parentNode The parent XML node of this section.
	 */
	protected void createSectionXML(final Map<String, byte[]> files, final Level level, final List<LevelXMLData> levels, final boolean fixedUrlsSuccess)
	{
		final LinkedList<com.redhat.contentspec.Node> levelData = level.getChildNodes();
		
		// Add the section and topics for this level to the chapter.xml
		for (com.redhat.contentspec.Node node: levelData) {
			
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			if (node instanceof Level) {
				Level childLevel = (Level)node;
				
				final LevelXMLData section = new LevelXMLData("section", childLevel.getTitle(), childLevel.getUniqueLinkId(fixedUrlsSuccess));
				levels.add(section);
				
				// Ignore sections that have no spec topics
				if (childLevel.hasSpecTopics())
				{
					// Add this sections child sections/topics
					createSectionXML(files, childLevel, section.getChildLevels(), fixedUrlsSuccess);
				}
			} else if (node instanceof SpecTopic) {
				SpecTopic specTopic = (SpecTopic)node;
				String topicFileName;
				
				if (fixedUrlsSuccess)
					topicFileName = specTopic.getTopic().getXrefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID);
				else
					topicFileName = specTopic.getTopic().getXRefID();
				
				if (specTopic.getDuplicateId() != null)
					topicFileName += "-" + specTopic.getDuplicateId();			
					
				topicFileName += ".xml";
				
				final LevelXMLData topic = new LevelXMLData("topics/" + topicFileName);
				levels.add(topic);
				
				final VelocityContext topicCtx = specDatabase.getSpecTopicContext(specTopic);
				topicCtx.put("templateFile", resourcePath + BuilderConstants.TOPIC_TEMPLATE_NAME);
				topicCtx.put("escapedBookTitle", escapedTitle);
				
				final String topicXML = mergeTopicContextWithDocument(specTopic.getXmlDocument(), topicCtx, preambleTemplate);
				files.put(BOOK_TOPICS_FOLDER + topicFileName, topicXML.getBytes());
			}
		}
	}
	
	private void addImagesToBook(final HashMap<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		/* Load the database constants */
		final byte[] failpenguinPng = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.FAILPENGUIN_PNG_ID, "").getValue();

		/* download the image files that were identified in the processing stage */
		int imageProgress = 0;
		final int imageTotal = this.imageLocations.size();

		for (final TopicImageData<T> imageLocation : this.imageLocations)
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			boolean success = false;

			final int extensionIndex = imageLocation.getImageName().lastIndexOf(".");
			final int pathIndex = imageLocation.getImageName().lastIndexOf("/");

			if (/* characters were found */
			extensionIndex != -1 && pathIndex != -1 &&
			/* the path character was found before the extension */
			extensionIndex > pathIndex)
			{
				try
				{
					/*
					 * The file name minus the extension should be an integer
					 * that references an ImageFile record ID.
					 */
					final String topicID = imageLocation.getImageName().substring(pathIndex + 1, extensionIndex);
					final ImageV1 imageFile = restManager.getRESTClient().getJSONImage(Integer.parseInt(topicID), "");

					if (imageFile != null)
					{
						success = true;
						files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), imageFile.getImageData());
					}
					else
					{
						errorDatabase.addError(imageLocation.getTopic(), "ImageFile ID " + topicID + " from image location " + imageLocation + " was not found!");
						log.error("ImageFile ID " + topicID + " from image location " + imageLocation + " was not found!");
					}
				}
				catch (final Exception ex)
				{
					success = false;
					errorDatabase.addError(imageLocation.getTopic(), imageLocation + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					log.error(ExceptionUtilities.getStackTrace(ex));
				}
			}

			/* put in a place holder */
			if (!success)
			{
				files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), failpenguinPng);
			}

			final float progress = (float) imageProgress / (float) imageTotal * 100;
			log.info("\tDownloading Images " + Math.round(progress) + "% done");

			++imageProgress;
		}
	}
	
	/**
	 * Builds the Author_Group.xml using the assigned writers for topics inside of the content specification.
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	@SuppressWarnings("unchecked")
	private void buildAuthorGroup(final ContentSpec contentSpec, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tBuilding Author_Group.xml");
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		// Get the mapping of authors using the topics inside the content spec
		final LinkedHashMap<Integer, TagV1> authors = new LinkedHashMap<Integer, TagV1>();
		for (Integer topicId: specDatabase.getTopicIds()) {
			final T topic = (T) specDatabase.getSpecTopicsForTopicID(topicId).get(0).getTopic();
			final List<TagV1> authorTags = topic.getTagsInCategoriesByID(CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));
			if (authorTags.size() > 0) {
				for (TagV1 author: authorTags) {
					if (!authors.containsKey(author.getId())) {
						authors.put(author.getId(), author);
					}
				}
			}
		}
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		final List<AuthorInformation> authorList = new ArrayList<AuthorInformation>();
		if (!authors.isEmpty()) {
			// For each author attempt to find the author information records and populate Author_Group.xml.
			for (Integer authorId: authors.keySet()) {
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					shutdown.set(true);
					return;
				}
				
				// Add any authors who have their author details setup
				AuthorInformation authorInfo = reader.getAuthorInformation(authorId);
				if (authorInfo != null) 
				{
					authorList.add(authorInfo);
				}
			}	
		}
		
		final VelocityContext authorGroupCtx = new VelocityContext();
		authorGroupCtx.put("authors", authorList);
		authorGroupCtx.put("templateFile", resourcePath + BuilderConstants.AUTHOR_GROUP_TEMPLATE_NAME);
		authorGroupCtx.put("escapedBookTitle", escapedTitle);
		
		final StringWriter authorGroupWriter = new StringWriter();
		preambleTemplate.merge(authorGroupCtx, authorGroupWriter);
		
		// Add the Author_Group.xml to the book
		files.put(BOOK_LOCALE_FOLDER + "Author_Group.xml", authorGroupWriter.toString().getBytes());
	}
	
	/**
	 * Builds the revision history using the requester of the build.
	 * 
	 * @param requester The user who requested the build action
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	private void buildRevisionHistory(final ContentSpec contentSpec, final UserV1 requester, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException 
	{
		log.info("\tBuilding Revision_History.xml");
		
		final List<TagV1> authorList = requester == null ? new ArrayList<TagV1>() : reader.getTagsByName(requester.getName());
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		final List<String> revisionDescriptions = new ArrayList<String>();
		
		// An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
		final AuthorInformation authorInfo;
		if (authorList.size() == 1) {
			authorInfo = reader.getAuthorInformation(authorList.get(0).getId());
			if (authorInfo != null) {
				if (contentSpec.getId() > 0)
					revisionDescriptions.add(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(contentSpec.getId())) + (authorInfo.getAuthorId() > 0 ? " by " + requester.getName() : ""));
			} else {
				if (contentSpec.getId() > 0)
					revisionDescriptions.add(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(contentSpec.getId())));
			}
		// No assigned writer exists for the uploader so use default values
		} else {
			authorInfo = null;
			if (contentSpec.getId() > 0)
				revisionDescriptions.add(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(contentSpec.getId())));
		}
		
		final VelocityContext revisionHistoryCtx = new VelocityContext();
		revisionHistoryCtx.put("author", authorInfo);
		revisionHistoryCtx.put("revdescriptions", revisionDescriptions);
		revisionHistoryCtx.put("templateFile", resourcePath + BuilderConstants.AUTHOR_GROUP_TEMPLATE_NAME);
		revisionHistoryCtx.put("escapedBookTitle", escapedTitle);
		
		final StringWriter revisionHistoryWriter = new StringWriter();
		preambleTemplate.merge(revisionHistoryCtx, revisionHistoryWriter);
		
		// Add the revision history to the book
		files.put(BOOK_LOCALE_FOLDER + "Revision_History.xml", revisionHistoryWriter.toString().getBytes());
	}
	
	private String buildErrorChapter(final String locale)
	{
		log.info("\tBuilding Error Chapter");
		
		String errorItemizedLists = "";

		if (errorDatabase.hasItems(locale))
		{
			for (final TopicErrorData<T> topicErrorData : errorDatabase.getErrors(locale))
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return null;
				}
				
				final T topic = topicErrorData.getTopic();

				final List<String> topicErrorItems = new ArrayList<String>();

				final String tags = topic.getCommaSeparatedTagList();
				final String url = topic.getSkynetURL();

				topicErrorItems.add(DocbookUtils.buildListItem("INFO: " + tags));
				topicErrorItems.add(DocbookUtils.buildListItem("INFO: <ulink url=\"" + url + "\">Topic URL</ulink>"));

				for (final String error : topicErrorData.getItemsOfType(TopicErrorDatabase.ERROR))
					topicErrorItems.add(DocbookUtils.buildListItem("ERROR: " + error));

				for (final String warning : topicErrorData.getItemsOfType(TopicErrorDatabase.WARNING))
					topicErrorItems.add(DocbookUtils.buildListItem("WARNING: " + warning));

				/*
				 * this should never be false, because a topic will only be
				 * listed in the errors collection once a error or warning has
				 * been added. The count of 2 comes from the standard list items
				 * we added above for the tags and url.
				 */
				if (topicErrorItems.size() > 2)
				{
					final String title;
					if (topic instanceof TranslatedTopicV1)
					{
						final TranslatedTopicV1 translatedTopic = (TranslatedTopicV1) topic;
						title = "Topic ID " + translatedTopic.getTopicId() + ", Revision " + translatedTopic.getTopicRevision();
					}
					else
					{
						title = "Topic ID " + topic.getId();
					}
					final String id = topic.getErrorXRefID();

					errorItemizedLists += DocbookUtils.wrapListItems(topicErrorItems, title, id);
				}
			}
		}
		else
		{
			errorItemizedLists = "<para>No Errors Found</para>";
		}

		return DocbookUtils.buildChapter(errorItemizedLists, "Compiler Output");
	}
	
	@SuppressWarnings("unchecked")
	private void processImageLocations()
	{
		for (final Integer topicId : specDatabase.getTopicIds())
		{
			final SpecTopic specTopic = specDatabase.getSpecTopicsForTopicID(topicId).get(0);
			final T topic = (T) specTopic.getTopic();
			
			/*
			 * Images have to be in the image folder in Publican. Here we loop
			 * through all the imagedata elements and fix up any reference to an
			 * image that is not in the images folder.
			 */
			final List<Node> images = this.getImages(specTopic.getXmlDocument());
	
			for (final Node imageNode : images)
			{
				final NamedNodeMap attributes = imageNode.getAttributes();
				if (attributes != null)
				{
					final Node fileRefAttribute = attributes.getNamedItem("fileref");
	
					if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/"))
					{
						fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
					}
	
					imageLocations.add(new TopicImageData<T>(topic, fileRefAttribute.getNodeValue()));
				}
			}
		}
	}
	
	/**
	 * @param node
	 *            The node to search for imagedata elements in
	 * @return Search any imagedata elements found in the supplied node
	 */
	private List<Node> getImages(final Node node)
	{
		final List<Node> images = new ArrayList<Node>();
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			final Node child = children.item(i);

			if (child.getNodeName().equals("imagedata"))
			{
				images.add(child);
			}
			else
			{
				images.addAll(getImages(child));
			}
		}
		return images;
	}

	private String mergeTopicContextWithDocument(final Document doc, final VelocityContext context) 
	{
		return mergeTopicContextWithDocument(doc, context, topicTemplate);
	}
	
	private String mergeTopicContextWithDocument(final Document doc, final VelocityContext context, final Template template) 
	{
		/* Remove the title element */
		if (doc != null)
		{
			final NodeList nodes = doc.getDocumentElement().getChildNodes();
			for (int i = 0; i < nodes.getLength(); i++)
			{
				final Node node = nodes.item(i);
				if (node.getNodeName().equals("title"))
				{
					doc.getDocumentElement().removeChild(node);
				}
			}
			
			/* Get the xml of the current document */
			final String xml = XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true);
			
			/* Remove the section tags from the xml */
			final String fixedXml = xml.replaceAll("^(\\s)*<section.*>(\\r)?(\\n)?", "").replaceAll("</section>(\\r)?(\\n)?$", "");
			
			context.put("content", fixedXml);
		}
		
		/* Convert the template to a document */
		final StringWriter topicWriter = new StringWriter();
		template.merge(context, topicWriter);
		
		final String retValue = topicWriter.toString();
		return retValue;
	}
	
	/**
	 * This method does a pass over all the topics returned by the query and
	 * attempts to create unique Fixed URL if one does not already exist.
	 * 
	 * @return true if fixed url property tags were able to be created for all
	 *         topics, and false otherwise   
	 */
	private boolean setFixedURLsPass(final BaseRestCollectionV1<TopicV1> topics)
	{
		log.info("Doing Fixed URL Pass");
		
		int tries = 0;
		boolean success = false;

		while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success)
		{
			
			++tries;

			try
			{
				final BaseRestCollectionV1<TopicV1> updateTopics = new BaseRestCollectionV1<TopicV1>();
				
				final Set<String> processedFileNames = new HashSet<String>();

				for (final TopicV1 topic : topics.getItems())
				{
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return false;
					}

					final PropertyTagV1 existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

					if (existingUniqueURL == null || !existingUniqueURL.isValid())
					{
						/*
						 * generate the base url
						 */
						String baseUrlName = createURLTitle(topic.getTitle());

						/* generate a unique fixed url */
						String postFix = "";

						for (int uniqueCount = 1; uniqueCount <= BuilderConstants.MAXIMUM_SET_PROP_TAG_NAME_RETRY; ++uniqueCount)
						{
							final String query = "query;propertyTag1=" + CommonConstants.FIXED_URL_PROP_TAG_ID + URLEncoder.encode(" " + baseUrlName + postFix, "UTF-8");
							final BaseRestCollectionV1<TopicV1> queryTopics = restManager.getRESTClient().getJSONTopicsWithQuery(new PathSegmentImpl(query, false), "");

							if (queryTopics.getSize() != 0)
							{
								postFix = uniqueCount + "";
							}
							else
							{
								break;
							}
						}
						
						// Check if the app should be shutdown
						if (isShuttingDown.get()) {
							return false;
						}
						
						/*
						 * persist the new fixed url, as long as we are not
						 * looking at a landing page topic
						 */
						if (topic.getId() >= 0)
						{
							final PropertyTagV1 propertyTag = new PropertyTagV1();
							propertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
							propertyTag.setValue(baseUrlName + postFix);
							propertyTag.setAddItem(true);

							final BaseRestCollectionV1<PropertyTagV1> updatePropertyTags = new BaseRestCollectionV1<PropertyTagV1>();
							updatePropertyTags.addItem(propertyTag);

							/* remove any old fixed url property tags */
							if (topic.getProperties() != null && topic.getProperties().getItems() != null)
							{
								for (final PropertyTagV1 existing : topic.getProperties().getItems())
								{
									if (existing.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID))
									{
										final PropertyTagV1 removePropertyTag = new PropertyTagV1();
										removePropertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
										removePropertyTag.setValue(existing.getValue());
										removePropertyTag.setRemoveItem(true);
										updatePropertyTags.addItem(removePropertyTag);
									}
								}
							}

							final TopicV1 updateTopic = new TopicV1();
							updateTopic.setId(topic.getId());
							updateTopic.setPropertiesExplicit(updatePropertyTags);

							updateTopics.addItem(updateTopic);
							processedFileNames.add(baseUrlName + postFix);
						}
					}
				}

				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					restManager.getRESTClient().updateJSONTopics("", updateTopics);
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return false;
				}

				/* If we got here, then the REST update went ok */
				success = true;

				/* copy the topics fixed url properties to our local collection */
				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					for (final TopicV1 topicWithFixedUrl : updateTopics.getItems())
					{
						for (final TopicV1 topic : topics.getItems())
						{
							final PropertyTagV1 fixedUrlProp = topicWithFixedUrl.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
							
							if (topic != null && topicWithFixedUrl.getId().equals(topic.getId()))
							{
								BaseRestCollectionV1<PropertyTagV1> properties = topic.getProperties();
								if (properties == null) {
									properties = new BaseRestCollectionV1<PropertyTagV1>();
								} else if (properties.getItems() != null) {
									// remove any current url's
									for (PropertyTagV1 prop: properties.getItems()) {
										if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
											properties.getItems().remove(prop);
										}
									}
								}
								
								if (fixedUrlProp != null)
									properties.addItem(fixedUrlProp);
							}
							
							/*
							 * we also have to copy the fixed urls into the
							 * related topics
							 */
							for (final TopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
							{
								if (topicWithFixedUrl.getId().equals(relatedTopic.getId()))
								{
									BaseRestCollectionV1<PropertyTagV1> relatedTopicProperties = relatedTopic.getProperties();
									if (relatedTopicProperties == null) {
										relatedTopicProperties = new BaseRestCollectionV1<PropertyTagV1>();
									} else if (relatedTopicProperties.getItems() != null) {
										// remove any current url's
										for (PropertyTagV1 prop: relatedTopicProperties.getItems()) {
											if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
												relatedTopicProperties.getItems().remove(prop);
											}
										}
									}

									if (fixedUrlProp != null)
										relatedTopicProperties.addItem(fixedUrlProp);
								}
							}
						}
					}
				}
			}
			catch (final Exception ex)
			{
				/*
				 * Dump the exception to the command prompt, and restart the
				 * loop
				 */
				log.error(ExceptionUtilities.getStackTrace(ex));
			}
		}

		/* did we blow the try count? */
		return success;
	}
	
	/**
	 * Creates the URL specific title for a topic or level
	 * 
	 * @param title The title that will be used to create the URL Title
	 * @return The URL representation of the title;
	 */
	private String createURLTitle(final String title) {
		String baseTitle = new String(title);
		
		/*
		 * Check if the title starts with an invalid sequence
		 */
		final NamedPattern invalidSequencePattern = NamedPattern.compile(STARTS_WITH_INVALID_SEQUENCE_RE);
		final NamedMatcher invalidSequenceMatcher = invalidSequencePattern.matcher(baseTitle);
		
		if (invalidSequenceMatcher.find())
		{
			baseTitle = invalidSequenceMatcher.group("EverythingElse");
		}
		
		/*
		 * start by removing any prefixed numbers (you can't
		 * start an xref id with numbers)
		 */
		final NamedPattern pattern = NamedPattern.compile(STARTS_WITH_NUMBER_RE);
		final NamedMatcher matcher = pattern.matcher(baseTitle);

		if (matcher.find())
		{
			try
			{
				final String numbers = matcher.group("Numbers");
				final String everythingElse = matcher.group("EverythingElse");

				if (numbers != null && everythingElse != null)
				{
					final NumberFormat formatter = new RuleBasedNumberFormat(RuleBasedNumberFormat.SPELLOUT);
					final String numbersSpeltOut = formatter.format(Integer.parseInt(numbers));
					baseTitle = numbersSpeltOut + everythingElse;

					// Capitalize the first character
					if (baseTitle.length() > 0)
						baseTitle = baseTitle.substring(0, 1).toUpperCase() + baseTitle.substring(1, baseTitle.length());
				}
			}
			catch (final Exception ex)
			{
				log.error(ExceptionUtilities.getStackTrace(ex));
			}
		}
		
		// Escape the title
		String escapedTitle = DocBookUtilities.escapeTitle(baseTitle);
		while (escapedTitle.indexOf("__") != -1)
			escapedTitle = escapedTitle.replaceAll("__", "_");
		
		return escapedTitle;
	}
}
