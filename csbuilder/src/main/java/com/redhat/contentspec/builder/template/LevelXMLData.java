package com.redhat.contentspec.builder.template;

import java.util.LinkedList;
import java.util.List;

public class LevelXMLData {

	private final String elementName;
	private final String elementId;
	private final String fileName;
	private final String title;
	
	private final List<LevelXMLData> childIncludes = new LinkedList<LevelXMLData>();
	
	public LevelXMLData(final String elementName, final String elementId, final String title)
	{
		this.elementName = elementName;
		this.fileName = null;
		this.title = title;
		this.elementId = elementId;
	}
	
	public LevelXMLData(final String fileName)
	{
		this.elementName = null;
		this.fileName = fileName;
		this.title = null;
		this.elementId = null;
	}

	public String getElementName()
	{
		return elementName;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String getFilename()
	{
		return fileName;
	}

	public List<LevelXMLData> getChildLevels() {
		return childIncludes;
	}
	
	public void addChildLevel(final LevelXMLData childLevel)
	{
		this.childIncludes.add(childLevel);
	}
	
	public boolean hasChildLevels()
	{
		return !childIncludes.isEmpty();
	}
	
	public boolean isExternalFile()
	{
		return fileName != null;
	}

	public String getElementId() {
		return elementId;
	}
}
