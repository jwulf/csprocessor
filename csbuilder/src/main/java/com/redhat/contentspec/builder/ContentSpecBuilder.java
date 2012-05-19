package com.redhat.contentspec.builder;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.ContentSpec;
import com.redhat.contentspec.rest.RESTManager;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import com.redhat.contentspec.interfaces.ShutdownAbleApp;
import com.redhat.ecs.commonutils.ZipUtilities;
import com.redhat.ecs.services.docbookcompiling.DocbookBuilderConstants;
import com.redhat.topicindex.rest.entities.*;
import com.redhat.topicindex.rest.exceptions.InternalProcessingException;
import com.redhat.topicindex.rest.exceptions.InvalidParameterException;

/**
 * 
 * A class that provides the ability to build a book from content specifications.
 * 
 * @author lnewson
 * @author alabbas
 */
public class ContentSpecBuilder implements ShutdownAbleApp {
	
	private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	
	@SuppressWarnings("unused")
	private final RESTReader reader;
	private final BlobConstantV1 rocbookdtd;
	private final RESTManager restManager;	
	private DocbookBuilder<?> docbookBuilder;

	public ContentSpecBuilder(final RESTManager restManager) throws InvalidParameterException, InternalProcessingException {
		this.restManager = restManager;
		reader = restManager.getReader();
		this.rocbookdtd = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
	}
	
	@Override
	public void shutdown() {
		isShuttingDown.set(true);
		if (docbookBuilder != null)
			docbookBuilder.shutdown();
	}

	@Override
	public boolean isShutdown() {
		return shutdown.get();
	}
	
	public int getNumWarnings() {
		return docbookBuilder == null ? 0 : docbookBuilder.getNumWarnings();
	}

	public int getNumErrors() {
		return docbookBuilder == null ? 0 : docbookBuilder.getNumErrors();
	}
	
	/**
	 * Builds a book into a zip file for the passed Content Specification
	 * 
	 * @param contentSpec The content specification that is to be built. It should have already been validated, if not errors may occur.
	 * @param requester The user who requested the book to be built.
	 * @return A byte array that is the zip file
	 * @throws Exception 
	 */
	public byte[] buildBook(final ContentSpec contentSpec, final UserV1 requester, final CSDocbookBuildingOptions builderOptions) throws Exception {
		if (contentSpec == null) throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
		if (requester == null) throw new BuilderCreationException("A user must be specified as the user who requested the build.");
		
		if (contentSpec.getLocale() == null || contentSpec.getLocale().equals("en-US"))
			docbookBuilder = new DocbookBuilder<TopicV1>(restManager, rocbookdtd, "en-US");
		else
			docbookBuilder = new DocbookBuilder<TranslatedTopicV1>(restManager, rocbookdtd, "en-US");
			
		final HashMap<String, byte[]> files = docbookBuilder.buildBook(contentSpec, requester, builderOptions, null);
		
		// Create the zip file
		byte[] zipFile = null;
		try {
			zipFile = ZipUtilities.createZip(files);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return zipFile;
	}
}