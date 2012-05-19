package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.velocity.VelocityContext;

import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.ecs.commonutils.CollectionUtilities;

public class SpecDatabase {

	private Map<Integer, Map<SpecTopic, VelocityContext>> specTopics = new HashMap<Integer, Map<SpecTopic, VelocityContext>>();
	private Map<String, List<SpecTopic>> specTopicsTitles = new HashMap<String, List<SpecTopic>>();
	private Map<String, List<Level>> specLevels = new HashMap<String, List<Level>>();
	
	public void add(final SpecTopic topic, final String escapedTitle)
	{
		if (topic == null) return;
				
		final Integer topicId = topic.getDBId();
		if (!specTopics.containsKey(topicId))
			specTopics.put(topicId, new LinkedHashMap<SpecTopic, VelocityContext>());
		
		if (!specTopicsTitles.containsKey(escapedTitle))
			specTopicsTitles.put(escapedTitle, new LinkedList<SpecTopic>());
		
		if (specTopics.get(topicId).size() > 0 || specTopicsTitles.get(escapedTitle).size() > 0)
		{
			int duplicateId = specTopics.get(topicId).size();
			
			if (specTopicsTitles.get(escapedTitle).size() > duplicateId)
				duplicateId = specTopicsTitles.get(escapedTitle).size();
			
			topic.setDuplicateId(Integer.toString(specTopics.get(topicId).size()));
		}
		
		specTopics.get(topicId).put(topic, null);
		specTopicsTitles.get(escapedTitle).add(topic);
	}
	
	public void add(final Level level, final String escapedTitle)
	{
		if (level == null) return;
		
		if (!specLevels.containsKey(escapedTitle))
			specLevels.put(escapedTitle, new LinkedList<Level>());
		
		if (specLevels.get(escapedTitle).size() > 0)
			level.setDuplicateId(Integer.toString(specLevels.get(escapedTitle).size()));
		
		specLevels.get(escapedTitle).add(level);
	}
	
	public List<Integer> getTopicIds()
	{
		return CollectionUtilities.toArrayList(specTopics.keySet());
	}
	
	public boolean isUniqueSpecTopic(final SpecTopic topic)
	{
		return specTopics.containsKey(topic.getDBId()) ? specTopics.get(topic.getDBId()).size() == 1 : false;
	}
	
	public List<SpecTopic> getSpecTopicsForTopicID(final Integer topicId)
	{
		if (specTopics.containsKey(topicId))
		{
			return CollectionUtilities.toArrayList(specTopics.get(topicId).keySet());
		}
		
		return new LinkedList<SpecTopic>();
	}
	
	public void setSpecTopicContext(final SpecTopic specTopic, final VelocityContext topicCtx)
	{
		if (specTopics.containsKey(specTopic.getDBId()) && specTopics.get(specTopic.getDBId()).containsKey(specTopic))
		{
			specTopics.get(specTopic.getDBId()).put(specTopic, topicCtx);
		}
	}

	public VelocityContext getSpecTopicContext(final SpecTopic specTopic)
	{
		if (specTopics.containsKey(specTopic.getDBId()) && specTopics.get(specTopic.getDBId()).containsKey(specTopic))
		{
			return specTopics.get(specTopic.getDBId()).get(specTopic);
		}
		return null;
	}
	
	public List<SpecTopic> getAllSpecTopics()
	{
		final ArrayList<SpecTopic> specTopics = new ArrayList<SpecTopic>();
		for (final Integer topicId: this.specTopics.keySet())
		{
			specTopics.addAll(this.specTopics.get(topicId).keySet());
		}
		
		return specTopics;
	}
	
	public List<Level> getAllLevels()
	{
		final ArrayList<Level> levels = new ArrayList<Level>();
		for (final String levelTitle : this.specLevels.keySet())
		{
			levels.addAll(this.specLevels.get(levelTitle));
		}
		
		return levels;
	}
}
