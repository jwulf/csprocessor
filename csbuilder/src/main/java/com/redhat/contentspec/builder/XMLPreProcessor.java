package com.redhat.contentspec.builder;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.contentspec.builder.utils.XMLUtilities;
import com.redhat.contentspec.entities.TargetRelationship;
import com.redhat.contentspec.entities.TopicRelationship;
import com.redhat.ecs.commonstructures.Pair;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.ExceptionUtilities;
import com.redhat.ecs.constants.CommonConstants;
import com.redhat.ecs.services.docbookcompiling.DocbookBuilderConstants;
import com.redhat.ecs.services.docbookcompiling.DocbookBuildingOptions;
import com.redhat.ecs.services.docbookcompiling.DocbookUtils;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.GenericInjectionPoint;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.GenericInjectionPointDatabase;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.InjectionListData;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.InjectionTopicData;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.TocTopicDatabase;
import com.redhat.ecs.sort.ExternalListSort;

import com.redhat.topicindex.rest.entities.BaseTopicV1;
import com.redhat.topicindex.rest.entities.PropertyTagV1;
import com.redhat.topicindex.rest.entities.TagV1;
import com.redhat.topicindex.rest.entities.TranslatedTopicV1;
import com.redhat.topicindex.rest.sort.TopicTitleSorter;
import com.redhat.topicindex.rest.sort.BaseTopicV1TitleComparator;

/**
 * This class takes the XML from a topic and modifies it to include and injected
 * content.
 */
public class XMLPreProcessor<T extends BaseTopicV1<T>>
{
	/**
	 * Used to identify that an <orderedlist> should be generated for the
	 * injection point
	 */
	protected static final int ORDEREDLIST_INJECTION_POINT = 1;
	/**
	 * Used to identify that an <itemizedlist> should be generated for the
	 * injection point
	 */
	protected static final int ITEMIZEDLIST_INJECTION_POINT = 2;
	/**
	 * Used to identify that an <xref> should be generated for the injection
	 * point
	 */
	protected static final int XREF_INJECTION_POINT = 3;
	/**
	 * Used to identify that an <xref> should be generated for the injection
	 * point
	 */
	protected static final int LIST_INJECTION_POINT = 4;
	/** Identifies a named regular expression group */
	protected static final String TOPICIDS_RE_NAMED_GROUP = "TopicIDs";
	/** This text identifies an option task in a list */
	protected static final String OPTIONAL_MARKER = "OPT:";
	/** The text to be prefixed to a list item if a topic is optional */
	protected static final String OPTIONAL_LIST_PREFIX = "Optional: ";
	/** A regular expression that identifies a topic id */
	protected static final String OPTIONAL_TOPIC_ID_RE = "(" + OPTIONAL_MARKER + "\\s*)?\\d+";
	/** A regular expression that identifies a topic id */
	protected static final String TOPIC_ID_RE = "\\d+";

	/**
	 * A regular expression that matches an InjectSequence custom injection
	 * point
	 */
	protected static final String CUSTOM_INJECTION_SEQUENCE_RE =
	/*
	 * start xml comment and 'InjectSequence:' surrounded by optional white
	 * space
	 */
	"\\s*InjectSequence:\\s*" +
	/*
	 * an optional comma separated list of digit blocks, and at least one digit
	 * block with an optional comma
	 */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_TOPIC_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_TOPIC_ID_RE + ",?))" +
	/* xml comment end */
	"\\s*";

	/** A regular expression that matches an InjectList custom injection point */
	protected static final String CUSTOM_INJECTION_LIST_RE =
	/* start xml comment and 'InjectList:' surrounded by optional white space */
	"\\s*InjectList:\\s*" +
	/*
	 * an optional comma separated list of digit blocks, and at least one digit
	 * block with an optional comma
	 */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_TOPIC_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_TOPIC_ID_RE + ",?))" +
	/* xml comment end */
	"\\s*";

	protected static final String CUSTOM_INJECTION_LISTITEMS_RE =
	/* start xml comment and 'InjectList:' surrounded by optional white space */
	"\\s*InjectListItems:\\s*" +
	/*
	 * an optional comma separated list of digit blocks, and at least one digit
	 * block with an optional comma
	 */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_TOPIC_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_TOPIC_ID_RE + ",?))" +
	/* xml comment end */
	"\\s*";

	protected static final String CUSTOM_ALPHA_SORT_INJECTION_LIST_RE =
	/*
	 * start xml comment and 'InjectListAlphaSort:' surrounded by optional white
	 * space
	 */
	"\\s*InjectListAlphaSort:\\s*" +
	/*
	 * an optional comma separated list of digit blocks, and at least one digit
	 * block with an optional comma
	 */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_TOPIC_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_TOPIC_ID_RE + ",?))" +
	/* xml comment end */
	"\\s*";

	/** A regular expression that matches an Inject custom injection point */
	protected static final String CUSTOM_INJECTION_SINGLE_RE =
	/* start xml comment and 'Inject:' surrounded by optional white space */
	"\\s*Inject:\\s*" +
	/* one digit block */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(" + OPTIONAL_TOPIC_ID_RE + "))" +
	/* xml comment end */
	"\\s*";

	/** A regular expression that matches an Inject Content Fragment */
	protected static final String INJECT_CONTENT_FRAGMENT_RE =
	/* start xml comment and 'Inject:' surrounded by optional white space */
	"\\s*InjectText:\\s*" +
	/* one digit block */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(" + TOPIC_ID_RE + "))" +
	/* xml comment end */
	"\\s*";

	/** A regular expression that matches an Inject Content Fragment */
	protected static final String INJECT_TITLE_FRAGMENT_RE =
	/* start xml comment and 'Inject:' surrounded by optional white space */
	"\\s*InjectTitle:\\s*" +
	/* one digit block */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(" + TOPIC_ID_RE + "))" +
	/* xml comment end */
	"\\s*";

	/**
	 * The noinject value for the role attribute indicates that an element
	 * should not be included in the Topic Fragment
	 */
	protected static final String NO_INJECT_ROLE = "noinject";

	public void processTopicBugzillaLink(final SpecTopic specTopic, final VelocityContext topicCtx, final DocbookBuildingOptions docbookBuildingOptions, final String buildName, final String searchTagsUrl, final Date buildDate)
	{		
		/* BUGZILLA LINK */
		try
		{
			final String instanceNameProperty = System.getProperty(CommonConstants.INSTANCE_NAME_PROPERTY);
			final String fixedInstanceNameProperty = instanceNameProperty == null ? "Not Defined" : instanceNameProperty;
			
			DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			
			String specifiedBuildName = "";
			if (docbookBuildingOptions != null && docbookBuildingOptions.getBuildName() != null)
				specifiedBuildName = docbookBuildingOptions.getBuildName();

			/* build up the elements that go into the bugzilla URL */
			String bugzillaProduct = null;
			String bugzillaComponent = null;
			String bugzillaVersion = null;
			String bugzillaKeywords = null;
			String bugzillaAssignedTo = null;
			final String bugzillaEnvironment = URLEncoder.encode("Instance Name: " + fixedInstanceNameProperty + "\nBuild: " + buildName + "\nBuild Filter: " + searchTagsUrl +"\nBuild Name: " + specifiedBuildName + "\nBuild Date: " + formatter.format(buildDate), "UTF-8");
			final String bugzillaBuildID = URLEncoder.encode(specTopic.getTopic().getBugzillaBuildId(), "UTF-8");

			/* look for the bugzilla options */
			if (specTopic.getTopic().getTags() != null && specTopic.getTopic().getTags().getItems() != null)
			{
				for (final TagV1 tag : specTopic.getTopic().getTags().getItems())
				{
					final PropertyTagV1 bugzillaProductTag = tag.getProperty(CommonConstants.BUGZILLA_PRODUCT_PROP_TAG_ID);
					final PropertyTagV1 bugzillaComponentTag = tag.getProperty(CommonConstants.BUGZILLA_COMPONENT_PROP_TAG_ID);
					final PropertyTagV1 bugzillaKeywordsTag = tag.getProperty(CommonConstants.BUGZILLA_KEYWORDS_PROP_TAG_ID);
					final PropertyTagV1 bugzillaVersionTag = tag.getProperty(CommonConstants.BUGZILLA_VERSION_PROP_TAG_ID);
					final PropertyTagV1 bugzillaAssignedToTag = tag.getProperty(CommonConstants.BUGZILLA_PROFILE_PROPERTY);

					if (bugzillaProduct == null && bugzillaProductTag != null)
						bugzillaProduct = URLEncoder.encode(bugzillaProductTag.getValue(), "UTF-8");

					if (bugzillaComponent == null && bugzillaComponentTag != null)
						bugzillaComponent = URLEncoder.encode(bugzillaComponentTag.getValue(), "UTF-8");

					if (bugzillaKeywords == null && bugzillaKeywordsTag != null)
						bugzillaKeywords = URLEncoder.encode(bugzillaKeywordsTag.getValue(), "UTF-8");

					if (bugzillaVersion == null && bugzillaVersionTag != null)
						bugzillaVersion = URLEncoder.encode(bugzillaVersionTag.getValue(), "UTF-8");

					if (bugzillaAssignedTo == null && bugzillaAssignedToTag != null)
						bugzillaAssignedTo = URLEncoder.encode(bugzillaAssignedToTag.getValue(), "UTF-8");
				}
			}
			
			/* we need at least a product*/
			if (bugzillaProduct != null)
			{	
				topicCtx.put("bugProduct", bugzillaProduct);
				topicCtx.put("bugComponent", bugzillaComponent);
				topicCtx.put("bugVersion", bugzillaVersion);
				topicCtx.put("bugKeywords", bugzillaKeywords);
				topicCtx.put("bugAssignedTo", bugzillaAssignedTo);
				topicCtx.put("bugEnvironment", bugzillaEnvironment);
				topicCtx.put("bugBuildId", bugzillaBuildID);
			}
		}
		catch (final Exception ex)
		{
			ExceptionUtilities.handleException(ex);
		}
	}

	/**
	 * Adds some debug information and links to the end of the topic
	 */
	public void processTopicAdditionalInfo(final SpecTopic specTopic, final VelocityContext topicCtx, final DocbookBuildingOptions docbookBuildingOptions, final String buildName, final String searchTagsUrl, final Date buildDate)
	{		
		// BUGZILLA LINK
		topicCtx.put("injectBugLink", docbookBuildingOptions != null && docbookBuildingOptions.getInsertBugzillaLinks());
		if (docbookBuildingOptions != null && docbookBuildingOptions.getInsertBugzillaLinks()) {
			processTopicBugzillaLink(specTopic, topicCtx, docbookBuildingOptions, buildName, searchTagsUrl, buildDate);
		}

		// SURVEY LINK
		topicCtx.put("injectSurveyLink", docbookBuildingOptions != null && docbookBuildingOptions.getInsertSurveyLink());

		/* searchTagsUrl will be null for internal (i.e. HTML rendering) builds */
		if (searchTagsUrl != null)
		{
			// VIEW IN SKYNET
			topicCtx.put("topicUrl", specTopic.getTopic().getSkynetURL());

			// SKYNET VERSION
			topicCtx.put("buildName", buildName);
			topicCtx.put("buildUrl",  searchTagsUrl);
		}
	}

	/**
	 * Takes a comma separated list of ints, and returns an array of Integers.
	 * This is used when processing custom injection points.
	 */
	private static List<InjectionTopicData> processTopicIdList(final String list)
	{
		/* find the individual topic ids */
		final String[] topicIDs = list.split(",");

		List<InjectionTopicData> retValue = new ArrayList<InjectionTopicData>(topicIDs.length);

		/* clean the topic ids */
		for (int i = 0; i < topicIDs.length; ++i)
		{
			final String topicId = topicIDs[i].replaceAll(OPTIONAL_MARKER, "").trim();
			final boolean optional = topicIDs[i].indexOf(OPTIONAL_MARKER) != -1;

			try
			{
				final InjectionTopicData topicData = new InjectionTopicData(Integer.parseInt(topicId), optional);
				retValue.add(topicData);
			}
			catch (final Exception ex)
			{
				/*
				 * these lists are discovered by a regular expression so we
				 * shouldn't have any trouble here with Integer.parse
				 */
				ExceptionUtilities.handleException(ex);
				retValue.add(new InjectionTopicData(-1, false));
			}
		}

		return retValue;
	}

	public List<Integer> processInjections(final Level level, final SpecTopic topic, final ArrayList<Integer> customInjectionIds, final Document xmlDocument, final DocbookBuildingOptions docbookBuildingOptions, final boolean usedFixedUrls)
	{
		/*
		 * this collection keeps a track of the injection point markers and the
		 * docbook lists that we will be replacing them with
		 */
		final HashMap<Node, InjectionListData> customInjections = new HashMap<Node, InjectionListData>();

		final List<Integer> errorTopics = new ArrayList<Integer>();

		errorTopics.addAll(processInjections(level, topic, customInjectionIds, customInjections, ORDEREDLIST_INJECTION_POINT, xmlDocument, CUSTOM_INJECTION_SEQUENCE_RE, null, docbookBuildingOptions, usedFixedUrls));
		errorTopics.addAll(processInjections(level, topic, customInjectionIds, customInjections, XREF_INJECTION_POINT, xmlDocument, CUSTOM_INJECTION_SINGLE_RE, null, docbookBuildingOptions, usedFixedUrls));
		errorTopics.addAll(processInjections(level, topic, customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, xmlDocument, CUSTOM_INJECTION_LIST_RE, null, docbookBuildingOptions, usedFixedUrls));
		errorTopics.addAll(processInjections(level, topic, customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, xmlDocument, CUSTOM_ALPHA_SORT_INJECTION_LIST_RE, new TopicTitleSorter<T>(), docbookBuildingOptions, usedFixedUrls));
		errorTopics.addAll(processInjections(level, topic, customInjectionIds, customInjections, LIST_INJECTION_POINT, xmlDocument, CUSTOM_INJECTION_LISTITEMS_RE, null, docbookBuildingOptions, usedFixedUrls));

		/*
		 * If we are not ignoring errors, return the list of topics that could
		 * not be injected
		 */
		if (errorTopics.size() != 0 && docbookBuildingOptions != null && !docbookBuildingOptions.getIgnoreMissingCustomInjections())
			return errorTopics;

		/* now make the custom injection point substitutions */
		for (final Node customInjectionCommentNode : customInjections.keySet())
		{
			final InjectionListData injectionListData = customInjections.get(customInjectionCommentNode);
			List<Element> list = null;

			/*
			 * this may not be true if we are not building all related topics
			 */
			if (injectionListData.listItems.size() != 0)
			{
				if (injectionListData.listType == ORDEREDLIST_INJECTION_POINT)
				{
					list = DocbookUtils.wrapOrderedListItemsInPara(xmlDocument, injectionListData.listItems);
				}
				else if (injectionListData.listType == XREF_INJECTION_POINT)
				{
					list = injectionListData.listItems.get(0);
				}
				else if (injectionListData.listType == ITEMIZEDLIST_INJECTION_POINT)
				{
					list = DocbookUtils.wrapItemizedListItemsInPara(xmlDocument, injectionListData.listItems);
				}
				else if (injectionListData.listType == LIST_INJECTION_POINT)
				{
					list = DocbookUtils.wrapItemsInListItems(xmlDocument, injectionListData.listItems);
				}
			}

			if (list != null)
			{
				for (final Element element : list)
				{
					customInjectionCommentNode.getParentNode().insertBefore(element, customInjectionCommentNode);
				}

				customInjectionCommentNode.getParentNode().removeChild(customInjectionCommentNode);
			}
		}

		return errorTopics;
	}

	@SuppressWarnings("unchecked")
	public List<Integer> processInjections(final Level level, final SpecTopic topic, final ArrayList<Integer> customInjectionIds, final HashMap<Node, InjectionListData> customInjections, final int injectionPointType, final Document xmlDocument, final String regularExpression,
			final ExternalListSort<Integer, T, InjectionTopicData> sortComparator, final DocbookBuildingOptions docbookBuildingOptions, final boolean usedFixedUrls)
	{
		final List<Integer> retValue = new ArrayList<Integer>();

		if (xmlDocument == null)
			return retValue;

		/* loop over all of the comments in the document */
		for (final Node comment : XMLUtilities.getComments(xmlDocument))
		{
			final String commentContent = comment.getNodeValue();

			/* compile the regular expression */
			final NamedPattern injectionSequencePattern = NamedPattern.compile(regularExpression);
			/* find any matches */
			final NamedMatcher injectionSequencematcher = injectionSequencePattern.matcher(commentContent);

			/* loop over the regular expression matches */
			while (injectionSequencematcher.find())
			{
				/*
				 * get the list of topics from the named group in the regular
				 * expression match
				 */
				final String reMatch = injectionSequencematcher.group(TOPICIDS_RE_NAMED_GROUP);

				/* make sure we actually found a matching named group */
				if (reMatch != null)
				{
					/* get the sequence of ids */
					final List<InjectionTopicData> sequenceIDs = processTopicIdList(reMatch);

					/*
					 * get the outgoing relationships
					 */
					final List<T> relatedTopics = (List<T>) topic.getTopic().getOutgoingRelationships().getItems();

					/*
					 * Create a TocTopicDatabase to hold the related topics. The
					 * TocTopicDatabase provides a convenient way to access
					 * these topics
					 */
					TocTopicDatabase<T> relatedTopicsDatabase = new TocTopicDatabase<T>();
					relatedTopicsDatabase.setTopics(relatedTopics);

					/* sort the InjectionTopicData list if required */
					if (sortComparator != null)
					{
						sortComparator.sort(relatedTopics, sequenceIDs);
					}

					/* loop over all the topic ids in the injection point */
					for (final InjectionTopicData sequenceID : sequenceIDs)
					{
						/*
						 * topics that are injected into custom injection points
						 * are excluded from the generic related topic lists at
						 * the beginning and end of a topic. adding the topic id
						 * here means that when it comes time to generate the
						 * generic related topic lists, we can skip this topic
						 */
						customInjectionIds.add(sequenceID.topicId);

						/*
						 * Pull the topic out of the list of related topics
						 */
						final T relatedTopic = relatedTopicsDatabase.getTopic(sequenceID.topicId);

						/*
						 * See if the topic is also available in the main
						 * database (if the main database is available)
						 */
						final boolean isInDatabase = level == null ? true : level.isSpecTopicInLevelByTopicID(sequenceID.topicId);

						/*
						 * It is possible that the topic id referenced in the
						 * injection point has not been related, or has not been
						 * included in the list of topics to process. This is a
						 * validity error
						 */
						if (relatedTopic != null && isInDatabase)
						{
							/*
							 * build our list
							 */
							List<List<Element>> list = new ArrayList<List<Element>>();

							/*
							 * each related topic is added to a string, which is
							 * stored in the customInjections collection. the
							 * customInjections key is the custom injection text
							 * from the source xml. this allows us to match the
							 * xrefs we are generating for the related topic
							 * with the text in the xml file that these xrefs
							 * will eventually replace
							 */
							if (customInjections.containsKey(comment))
								list = customInjections.get(comment).listItems;

							/* if the toc is null, we are building an internal page */
							if (level == null)
							{
								final String url = relatedTopic.getInternalURL();
								if (sequenceID.optional)
								{
									list.add(DocbookUtils.buildEmphasisPrefixedULink(xmlDocument, OPTIONAL_LIST_PREFIX, url, relatedTopic.getTitle()));
								}
								else
								{
									list.add(DocbookUtils.buildULink(xmlDocument, url, relatedTopic.getTitle()));
								}
							}
							else
							{
								final Integer topicId;
								if (relatedTopic instanceof TranslatedTopicV1)
								{
									topicId = ((TranslatedTopicV1) relatedTopic).getTopicId();
								}
								else
								{
									topicId = relatedTopic.getId();
								}
								
								final SpecTopic closestSpecTopic = topic.getClosestTopicByDBId(topicId, true);							
								if (sequenceID.optional)
								{
									list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, closestSpecTopic.getUniqueLinkId(usedFixedUrls)));
								}
								else
								{
									list.add(DocbookUtils.buildXRef(xmlDocument, closestSpecTopic.getUniqueLinkId(usedFixedUrls)));										
								}
							}						

							/*
							 * save the changes back into the customInjections
							 * collection
							 */
							customInjections.put(comment, new InjectionListData(list, injectionPointType));
						}
						else
						{
							retValue.add(sequenceID.topicId);
						}
					}
				}
			}
		}

		return retValue;
	}

	@SuppressWarnings("unchecked")
	public List<Integer> processGenericInjections(final Level level, final SpecTopic topic, final VelocityContext topicCtx, final ArrayList<Integer> customInjectionIds, final List<Pair<Integer, String>> topicTypeTagIDs, final DocbookBuildingOptions docbookBuildingOptions,
			final boolean usedFixedUrls)
	{
		final List<Integer> errors = new ArrayList<Integer>();

		if (topicCtx == null)
			return errors;

		/*
		 * this collection will hold the lists of related topics
		 */
		final GenericInjectionPointDatabase<T> relatedLists = new GenericInjectionPointDatabase<T>();

		/* wrap each related topic in a listitem tag */
		if (topic.getTopic().getOutgoingRelationships() != null && topic.getTopic().getOutgoingRelationships().getItems()!= null)
		{
			for (final BaseTopicV1<?> relatedTopic : topic.getTopic().getOutgoingRelationships().getItems())
			{
				
				final Integer topicId;
				if (relatedTopic instanceof TranslatedTopicV1)
				{
					topicId = ((TranslatedTopicV1) relatedTopic).getTopicId();
				}
				else
				{
					topicId = relatedTopic.getId();
				}
				
				/*
				 * don't process those topics that were injected into custom
				 * injection points
				 */
				if (!customInjectionIds.contains(topicId))
				{
					/* make sure the topic is available to be linked to */
					if (level != null && !level.isSpecTopicInLevelByTopicID(topicId))
					{
						if ((docbookBuildingOptions != null && !docbookBuildingOptions.getIgnoreMissingCustomInjections()))
							errors.add(relatedTopic.getId());
					}
					else
					{
						// loop through the topic type tags
						for (final Pair<Integer, String> primaryTopicTypeTag : topicTypeTagIDs)
						{
							/*
							 * see if we have processed a related topic with one
							 * of the topic type tags this may never be true if
							 * not processing all related topics
							 */
							if (relatedTopic.isTaggedWith(primaryTopicTypeTag.getFirst()))
							{
								relatedLists.addInjectionTopic(primaryTopicTypeTag, (T) relatedTopic);

								break;
							}
						}
					}
				}
			}
		}

		insertGenericInjectionLinks(level, topic, topicCtx, relatedLists, docbookBuildingOptions, usedFixedUrls);

		return errors;
	}

	/**
	 * The generic injection points are placed in well defined locations within
	 * a topics xml structure. This function takes the list of related topics
	 * and the topic type tags that are associated with them and injects them
	 * into the xml document.
	 */
	private void insertGenericInjectionLinks(final Level level, final SpecTopic topic, final VelocityContext topicCtx, final GenericInjectionPointDatabase<T> relatedLists, final DocbookBuildingOptions docbookBuildingOptions, final boolean usedFixedUrls)
	{
		final Map<String, List<Pair<String, String>>> relatedTopicsMap = new HashMap<String, List<Pair<String, String>>>();
		/*
		 * place the topics at the end of the topic. They will appear in the
		 * reverse order as the call to toArrayList()
		 */
		for (final Integer topTag : CollectionUtilities.toArrayList(DocbookBuilderConstants.REFERENCE_TAG_ID, DocbookBuilderConstants.TASK_TAG_ID, DocbookBuilderConstants.CONCEPT_TAG_ID, DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_ID))
		{
			for (final GenericInjectionPoint<T> genericInjectionPoint : relatedLists.getInjectionPoints())
			{
				if (genericInjectionPoint.getCategoryIDAndName().getFirst() == topTag)
				{
					final List<T> relatedTopics = genericInjectionPoint.getTopics();

					/* don't add an empty list */
					if (relatedTopics.size() != 0)
					{
						relatedTopicsMap.put(genericInjectionPoint.getCategoryIDAndName().getSecond(), new ArrayList<Pair<String, String>>());
						Collections.sort(relatedTopics, new BaseTopicV1TitleComparator<T>());

						for (final T relatedTopic : relatedTopics)
						{
							if (level == null)
							{
								relatedTopicsMap.get(genericInjectionPoint.getCategoryIDAndName().getSecond()).add(new Pair<String, String>(relatedTopic.getInternalURL(), relatedTopic.getTitle()));
							}
							else
							{
								final Integer topicId;
								if (relatedTopic instanceof TranslatedTopicV1)
								{
									topicId = ((TranslatedTopicV1) relatedTopic).getTopicId();
								}
								else
								{
									topicId = relatedTopic.getId();
								}
								
								final SpecTopic closestSpecTopic = topic.getClosestTopicByDBId(topicId, true);
								relatedTopicsMap.get(genericInjectionPoint.getCategoryIDAndName().getSecond()).add(new Pair<String, String>(closestSpecTopic.getUniqueLinkId(usedFixedUrls), closestSpecTopic.getTitle()));
							}

						}
					}
				}
			}
		}
		
		topicCtx.put("relatedTopics", relatedTopicsMap);
	}

	public static void processInternalImageFiles(final Document xmlDoc)
	{
		if (xmlDoc == null)
			return;

		final List<Node> imageDataNodes = XMLUtilities.getNodes(xmlDoc.getDocumentElement(), "imagedata");
		for (final Node imageDataNode : imageDataNodes)
		{
			final NamedNodeMap attributes = imageDataNode.getAttributes();
			final Node filerefAttribute = attributes.getNamedItem("fileref");
			if (filerefAttribute != null)
			{
				String imageId = filerefAttribute.getTextContent();
				imageId = imageId.replace("images/", "");
				final int periodIndex = imageId.lastIndexOf(".");
				if (periodIndex != -1)
					imageId = imageId.substring(0, periodIndex);

				/*
				 * at this point imageId should be an integer that is the id of
				 * the image uploaded in skynet. We will leave the validation of
				 * imageId to the ImageFileDisplay class.
				 */

				filerefAttribute.setTextContent("ImageFileDisplay.seam?imageFileId=" + imageId);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public List<Integer> processTopicContentFragments(final SpecTopic specTopic, final Document xmlDocument, final DocbookBuildingOptions docbookBuildingOptions)
	{
		final List<Integer> retValue = new ArrayList<Integer>();

		if (xmlDocument == null)
			return retValue;

		final Map<Node, ArrayList<Node>> replacements = new HashMap<Node, ArrayList<Node>>();

		/* loop over all of the comments in the document */
		for (final Node comment : XMLUtilities.getComments(xmlDocument))
		{
			final String commentContent = comment.getNodeValue();

			/* compile the regular expression */
			final NamedPattern injectionSequencePattern = NamedPattern.compile(INJECT_CONTENT_FRAGMENT_RE);
			/* find any matches */
			final NamedMatcher injectionSequencematcher = injectionSequencePattern.matcher(commentContent);

			/* loop over the regular expression matches */
			while (injectionSequencematcher.find())
			{
				/*
				 * get the list of topics from the named group in the regular
				 * expression match
				 */
				final String reMatch = injectionSequencematcher.group(TOPICIDS_RE_NAMED_GROUP);

				/* make sure we actually found a matching named group */
				if (reMatch != null)
				{
					try
					{
						if (!replacements.containsKey(comment))
							replacements.put(comment, new ArrayList<Node>());

						final Integer topicID = Integer.parseInt(reMatch);

						/*
						 * make sure the topic we are trying to inject has been
						 * related
						 */
						if (specTopic.getTopic().isRelatedTo(topicID))
						{
							final T relatedTopic = (T) specTopic.getTopic().getRelatedTopicByID(topicID);
							final Document relatedTopicXML = XMLUtilities.convertStringToDocument(relatedTopic.getXml());
							if (relatedTopicXML != null)
							{
								final Node relatedTopicDocumentElement = relatedTopicXML.getDocumentElement();
								final Node importedXML = xmlDocument.importNode(relatedTopicDocumentElement, true);

								/* ignore the section title */
								final NodeList sectionChildren = importedXML.getChildNodes();
								for (int i = 0; i < sectionChildren.getLength(); ++i)
								{
									final Node node = sectionChildren.item(i);
									if (node.getNodeName().equals("title"))
									{
										importedXML.removeChild(node);
										break;
									}
								}

								/* remove all with a role="noinject" attribute */
								removeNoInjectElements(importedXML);

								/*
								 * importedXML is a now section with no title,
								 * and no child elements with the noinject value
								 * on the role attribute. We now add its
								 * children to the Array in the replacements
								 * Map.
								 */

								final NodeList remainingChildren = importedXML.getChildNodes();
								for (int i = 0; i < remainingChildren.getLength(); ++i)
								{
									final Node child = remainingChildren.item(i);
									replacements.get(comment).add(child);
								}
							}
						}
						else if (docbookBuildingOptions != null && !docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							retValue.add(Integer.parseInt(reMatch));
						}
					}
					catch (final Exception ex)
					{
						ExceptionUtilities.handleException(ex);
					}
				}
			}
		}

		/*
		 * The replacements map now has a keyset of the comments mapped to a
		 * collection of nodes that the comment will be replaced with
		 */

		for (final Node comment : replacements.keySet())
		{
			final ArrayList<Node> replacementNodes = replacements.get(comment);
			for (final Node replacementNode : replacementNodes)
				comment.getParentNode().insertBefore(replacementNode, comment);
			comment.getParentNode().removeChild(comment);
		}

		return retValue;
	}

	protected static void removeNoInjectElements(final Node parent)
	{
		final NodeList childrenNodes = parent.getChildNodes();
		final ArrayList<Node> removeNodes = new ArrayList<Node>();

		for (int i = 0; i < childrenNodes.getLength(); ++i)
		{
			final Node node = childrenNodes.item(i);
			final NamedNodeMap attributes = node.getAttributes();
			if (attributes != null)
			{
				final Node roleAttribute = attributes.getNamedItem("role");
				if (roleAttribute != null)
				{
					final String[] roles = roleAttribute.getTextContent().split(",");
					for (final String role : roles)
					{
						if (role.equals(NO_INJECT_ROLE))
						{
							removeNodes.add(node);
							break;
						}
					}
				}
			}
		}

		for (final Node removeNode : removeNodes)
			parent.removeChild(removeNode);

		final NodeList remainingChildrenNodes = parent.getChildNodes();

		for (int i = 0; i < remainingChildrenNodes.getLength(); ++i)
		{
			final Node child = remainingChildrenNodes.item(i);
			removeNoInjectElements(child);
		}
	}

	@SuppressWarnings("unchecked")
	public List<Integer> processTopicTitleFragments(final SpecTopic specTopic, final Document xmlDocument, final DocbookBuildingOptions docbookBuildingOptions)
	{
		final List<Integer> retValue = new ArrayList<Integer>();

		if (xmlDocument == null)
			return retValue;

		final Map<Node, Node> replacements = new HashMap<Node, Node>();

		/* loop over all of the comments in the document */
		for (final Node comment : XMLUtilities.getComments(xmlDocument))
		{
			final String commentContent = comment.getNodeValue();

			/* compile the regular expression */
			final NamedPattern injectionSequencePattern = NamedPattern.compile(INJECT_TITLE_FRAGMENT_RE);
			/* find any matches */
			final NamedMatcher injectionSequencematcher = injectionSequencePattern.matcher(commentContent);

			/* loop over the regular expression matches */
			while (injectionSequencematcher.find())
			{
				/*
				 * get the list of topics from the named group in the regular
				 * expression match
				 */
				final String reMatch = injectionSequencematcher.group(TOPICIDS_RE_NAMED_GROUP);

				/* make sure we actually found a matching named group */
				if (reMatch != null)
				{
					try
					{
						if (!replacements.containsKey(comment))
							replacements.put(comment, null);

						final Integer topicID = Integer.parseInt(reMatch);

						/*
						 * make sure the topic we are trying to inject has been
						 * related
						 */
						if (specTopic.getTopic().isRelatedTo(topicID))
						{
							final T relatedTopic = (T) specTopic.getTopic().getRelatedTopicByID(topicID);
							final Element titleNode = xmlDocument.createElement("title");
							titleNode.setTextContent(relatedTopic.getTitle());
							replacements.put(comment, titleNode);
						}
						else if (docbookBuildingOptions != null && !docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							retValue.add(Integer.parseInt(reMatch));
						}
					}
					catch (final Exception ex)
					{
						ExceptionUtilities.handleException(ex);
					}
				}
			}
		}

		/* swap the comment nodes with the new title nodes */
		for (final Node comment : replacements.keySet())
		{
			final Node title = replacements.get(comment);
			comment.getParentNode().insertBefore(title, comment);
			comment.getParentNode().removeChild(comment);
		}

		return retValue;
	}
	
	public void processPrevRelationshipInjections(final SpecTopic topic, final VelocityContext topicCtx, final boolean useFixedUrls) {
		if (topic.getPrevTopicRelationships().isEmpty()) return;
		
		// Attempt to get the next topic relationships and process them
		List<TopicRelationship> previousList = topic.getPrevTopicRelationships();

		final List<String> previousTopicLinks = new ArrayList<String>();
		for (TopicRelationship prev: previousList) {
			final SpecTopic previousTopic = prev.getSecondaryRelationship();
			previousTopicLinks.add(previousTopic.getUniqueLinkId(useFixedUrls));
		}
		
		topicCtx.put("processLink",  topic.getParent().getUniqueLinkId(useFixedUrls));
		topicCtx.put("previousSteps",  previousTopicLinks);
	}
	
	public void processNextRelationshipInjections(final SpecTopic topic, final VelocityContext topicCtx, final boolean useFixedUrls) {
		if (topic.getNextTopicRelationships().isEmpty()) return;
		
		// Attempt to get the next topic relationships and process them
		List<TopicRelationship> nextList = topic.getNextTopicRelationships();

		final List<String> nextTopicLinks = new ArrayList<String>();
		for (TopicRelationship next: nextList) {
			final SpecTopic nextTopic = next.getSecondaryRelationship();
			nextTopicLinks.add(nextTopic.getUniqueLinkId(useFixedUrls));
		}
		
		topicCtx.put("processLink",  topic.getParent().getUniqueLinkId(useFixedUrls));
		topicCtx.put("nextSteps",  nextTopicLinks);
	}
	
	/*
	 * Process's a Content Specs Topic and adds in the prerequisite topic links
	 */
	public void processPrerequisiteInjections(final SpecTopic topic, final VelocityContext topicCtx, final boolean useFixedUrls) {
		if (topic.getPrerequisiteRelationships().isEmpty()) return;

		final List<String> list = new ArrayList<String>();
		
		// Add the Topic Prerequisites
		for (final TopicRelationship prereq: topic.getPrerequisiteTopicRelationships())
		{
			final SpecTopic relatedTopic = prereq.getSecondaryRelationship();
			list.add(relatedTopic.getUniqueLinkId(useFixedUrls));
		}
		
		// Add the Level Prerequisites
		for (final TargetRelationship prereq: topic.getPrerequisiteLevelRelationships())
		{
			Level relatedLevel = (Level) prereq.getSecondaryElement();
			list.add(relatedLevel.getUniqueLinkId(useFixedUrls));
		}
		
		topicCtx.put("prerequisites",  list);
	}
	
	public void processSeeAlsoInjections(final SpecTopic topic, final VelocityContext topicCtx, final boolean useFixedUrls) 
	{
		if (topic.getRelatedRelationships().isEmpty()) return;
		
		final List<String> list = new ArrayList<String>();
		
		// Add the Topic Relationships
		for (TopicRelationship prereq: topic.getRelatedTopicRelationships()) {
			SpecTopic relatedTopic = prereq.getSecondaryRelationship();
			
			list.add(relatedTopic.getUniqueLinkId(useFixedUrls));
		}
		
		// Add the Level Relationships
		for (TargetRelationship prereq: topic.getRelatedLevelRelationships()) {
			Level relatedLevel = (Level) prereq.getSecondaryElement();
			list.add(relatedLevel.getUniqueLinkId(useFixedUrls));
		}
		
		topicCtx.put("seealsos",  list);
	}

	public static String processDocumentType(final String xml)
	{
		assert xml != null : "The xml parameter can not be null";

		if (XMLUtilities.findDocumentType(xml) == null)
		{
			final String preamble = XMLUtilities.findPreamble(xml);
			final String fixedPreamble = preamble == null ? "" : preamble + "\n";
			final String fixedXML = preamble == null ? xml : xml.replace(preamble, "");

			return fixedPreamble + "<!DOCTYPE section PUBLIC \"-//OASIS//DTD DocBook XML V4.5//EN\" \"http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd\" []>\n" + fixedXML;
		}

		return xml;
	}
}
