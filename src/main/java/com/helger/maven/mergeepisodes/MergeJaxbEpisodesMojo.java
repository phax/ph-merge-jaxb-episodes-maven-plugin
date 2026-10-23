/*
 * Copyright (C) 2021-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.maven.mergeepisodes;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.annotation.Nonempty;
import com.helger.annotation.misc.Since;
import com.helger.annotation.style.VisibleForTesting;
import com.helger.base.array.ArrayHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.io.file.SimpleFileIO;
import com.helger.xml.ChildElementIterator;
import com.helger.xml.NodeListIterator;
import com.helger.xml.XMLFactory;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.write.XMLWriter;
import com.helger.xml.serialize.write.XMLWriterSettings;

/**
 * @author Philip Helger
 * @description Merge all mentioned JAXB episode files "sun-jaxb.episode" into a single one
 */
@Mojo (name = "merge-jaxb-episodes", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public final class MergeJaxbEpisodesMojo extends AbstractMojo
{
  private static final String OUTPUT_FOLDER = "merged-jaxb-episode";
  private static final String FILENAME = "sun-jaxb.episode";
  private static final String NS_JAKARTA = "https://jakarta.ee/xml/ns/jaxb";
  private static final String NS_SUN = "http://java.sun.com/xml/ns/jaxb";

  @Parameter (property = "project", required = true, readonly = true)
  private MavenProject project;

  /**
   * The base directory to start scanning recursively. Defaults to
   * <code>${project.build.directory}</code>
   */
  @Parameter (name = "baseDirectory", defaultValue = "${project.build.directory}", required = true)
  private File baseDirectory;

  /**
   * Be a verbose in what we are doing?
   */
  @Parameter (name = "verbose", defaultValue = "false", required = false)
  private boolean verbose = false;

  /**
   * The list of files to be merged, relative to the baseDirectory
   */
  @Parameter (name = "episodeFiles", defaultValue = "**/" + FILENAME, required = true)
  private String [] episodeFiles;

  /**
   * Use Jakarta Namespace (JAXB 3.0 and 4.0) or Sun Namespace (JAXB 2.x)
   */
  @Since ("0.0.4")
  @Parameter (name = "useJakarta", defaultValue = "true", required = false)
  private boolean useJakarta = true;

  public void setBaseDirectory (@NonNull final File aDir)
  {
    baseDirectory = aDir;
    if (!baseDirectory.isAbsolute ())
      baseDirectory = new File (project.getBasedir (), aDir.getPath ());
    if (!baseDirectory.exists ())
      getLog ().error ("Base directory " + baseDirectory.toString () + " does not exist!");
  }

  public void setVerbose (final boolean b)
  {
    verbose = b;
  }

  public void setUseJakarta (final boolean b)
  {
    useJakarta = b;
  }

  @NonNull
  private static String _getResString (@NonNull final Resource aRes)
  {
    return "[dir=" +
           aRes.getDirectory () +
           "; includes=" +
           aRes.getIncludes () +
           "; excludes=" +
           aRes.getExcludes () +
           "; targetPath=" +
           aRes.getTargetPath () +
           "]";
  }

  private void _listResources (@NonNull final String sReason)
  {
    final List <Resource> aRess = project.getBuild ().getResources ();
    getLog ().info ("Maven resources [" + aRess.size () + "] - " + sReason);
    for (final Resource aRes : aRess)
      getLog ().info ("  Resource: " + _getResString (aRes));
  }

  private String _getHeaderComment (final List <File> aMatches)
  {
    final StringBuilder aCommentSB = new StringBuilder ();
    aCommentSB.append ("This file was automatically created by ph-merge-jaxb-episodes-maven-plugin - do NOT edit.");
    aCommentSB.append ("\nThis file was made up of:");
    for (final File aFile : aMatches)
      aCommentSB.append ("\n  ").append (aFile.getPath ());
    aCommentSB.append ("\n\nThis file was written at ")
              .append (ZonedDateTime.now (ZoneId.systemDefault ()).toString ());
    return aCommentSB.toString ();
  }

  private static void _renameNamespace (@NonNull final Document aDoc,
                                        @NonNull final Element aElement,
                                        @NonNull final String sTargetNamespaceURI)
  {
    final String sNS = aElement.getNamespaceURI ();
    Element aCurrent = aElement;
    if (NS_JAKARTA.equals (sNS) || NS_SUN.equals (sNS))
      aCurrent = (Element) aDoc.renameNode (aElement, sTargetNamespaceURI, aElement.getLocalName ());
    for (final Element aChild : new CommonsArrayList <> (new ChildElementIterator (aCurrent)))
      _renameNamespace (aDoc, aChild, sTargetNamespaceURI);
  }

  /**
   * Merge the content of all provided episode files into a single episode document.
   *
   * @param aMatches
   *        The episode files to merge. May not be empty.
   * @param bUseJakarta
   *        <code>true</code> to create a Jakarta episode, <code>false</code> to create a Sun
   *        episode. The namespace of the source files is irrelevant and converted if necessary.
   * @param sHeaderComment
   *        The comment to be added at the beginning of the file. May be <code>null</code>.
   * @param aLog
   *        Optional logger for verbose output. May be <code>null</code>.
   * @return The serialized merged episode file.
   * @throws MojoExecutionException
   *         If one of the files is not a valid episode file
   */
  @VisibleForTesting
  @NonNull
  static byte [] mergeEpisodes (@NonNull @Nonempty final ICommonsList <File> aMatches,
                                final boolean bUseJakarta,
                                @Nullable final String sHeaderComment,
                                @Nullable final Log aLog) throws MojoExecutionException
  {
    final String sTargetNamespaceURI = bUseJakarta ? NS_JAKARTA : NS_SUN;

    final Document aTargetDoc = XMLFactory.newDocument ();

    // <bindings xmlns="..." if-exists="true" version="3.0|2.1">
    final Element aTargetRoot = (Element) aTargetDoc.appendChild (aTargetDoc.createElementNS (sTargetNamespaceURI,
                                                                                              "bindings"));
    aTargetRoot.setAttribute ("if-exists", "true");
    aTargetRoot.setAttribute ("version", bUseJakarta ? "3.0" : "2.1");
    if (sHeaderComment != null)
      aTargetRoot.appendChild (aTargetDoc.createComment (sHeaderComment));

    for (final File aFile : aMatches)
    {
      if (aLog != null)
        aLog.info ("Parsing XML file '" + aFile.getPath () + "'");

      final Document aExistingDoc = DOMReader.readXMLDOM (aFile);
      if (aExistingDoc == null)
        throw new MojoExecutionException ("The file '" + aFile.getAbsolutePath () + "' is invalid XML");

      final Element aExistingRoot = aExistingDoc.getDocumentElement ();
      if (!"bindings".equals (aExistingRoot.getLocalName ()))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected element name)");

      final String sNS = aExistingRoot.getNamespaceURI ();
      if (!NS_JAKARTA.equals (sNS) && !NS_SUN.equals (sNS))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected namespace URI)");

      // Copy to target document
      for (final Node aChildNode : NodeListIterator.createChildNodeIterator (aExistingRoot))
      {
        aTargetRoot.appendChild (aTargetDoc.importNode (aChildNode, true));
      }
    }

    // Convert to target namespace afterwards
    for (final Element aChild : new CommonsArrayList <> (new ChildElementIterator (aTargetRoot)))
      _renameNamespace (aTargetDoc, aChild, sTargetNamespaceURI);

    // Namespace prefixes used inside attribute values (like scd="x-schema::tns") are not
    // recognized as "used" by the serializer, so the existing declarations must be kept
    return XMLWriter.getNodeAsBytes (aTargetDoc, new XMLWriterSettings ().setUseExistingNamespaceDeclarations (true));
  }

  public void execute () throws MojoExecutionException
  {
    if (baseDirectory == null)
      throw new MojoExecutionException ("No baseDirectory specified!");
    if (ArrayHelper.isEmpty (episodeFiles))
      throw new MojoExecutionException ("No episode file is specified");

    final ICommonsList <File> aMatches = new CommonsArrayList <> ();
    {
      // Scan the provided directory
      final DirectoryScanner aScanner = new DirectoryScanner ();
      aScanner.setBasedir (baseDirectory);
      aScanner.setIncludes (episodeFiles);
      // Exclude the result of a previous builds as well as our output file
      aScanner.setExcludes (new String [] { "classes/META-INF/" + FILENAME, "**/" + OUTPUT_FOLDER + "/" + FILENAME });
      aScanner.setCaseSensitive (true);
      aScanner.scan ();

      final String [] aFilenames = aScanner.getIncludedFiles ();
      if (aFilenames != null)
        for (final String sFilename : aFilenames)
        {
          final File aFile = new File (baseDirectory, sFilename);
          getLog ().info ("Found episode file to merge: " + aFile.getPath ());
          aMatches.add (aFile);
        }
    }

    if (aMatches.size () <= 1)
    {
      getLog ().warn ("Found " + aMatches.size () + " episode files - nothing to merge");
    }
    else
    {
      if (verbose)
        getLog ().info ("Merging " + aMatches.size () + " files using XML parsing/cloning");

      final byte [] aTargetXML = mergeEpisodes (aMatches,
                                                useJakarta,
                                                _getHeaderComment (aMatches),
                                                verbose ? getLog () : null);
      final File fTarget = new File (project.getBuild ().getDirectory () + "/" + OUTPUT_FOLDER, FILENAME);

      if (verbose)
        getLog ().info ("Writing combined " + FILENAME + " to '" + fTarget.getAbsolutePath () + "'");

      if (SimpleFileIO.writeFile (fTarget, aTargetXML).isFailure ())
        throw new MojoExecutionException ("Failed to write merged JAXB episode file to '" +
                                          fTarget.getAbsolutePath () +
                                          "'");

      if (verbose)
        _listResources ("Before modification");

      // Remove all existing Resources that contain "META-INF/sun-jaxb.episode"
      {
        final String sSearchName1 = "META-INF/" + FILENAME;
        final String sSearchName2 = "META-INF\\" + FILENAME;

        // Copy list to modify it
        for (final Resource aRes : new CommonsArrayList <> (project.getBuild ().getResources ()))
        {
          if (aRes.getIncludes ().contains (sSearchName1) || aRes.getIncludes ().contains (sSearchName2))
          {
            // Include is explicitly mentioned
            aRes.getIncludes ().remove (sSearchName1);
            aRes.getIncludes ().remove (sSearchName2);
            if (verbose)
              getLog ().info ("Removed '" + sSearchName1 + "' from: " + _getResString (aRes));

            if (aRes.getIncludes ().isEmpty ())
            {
              // Remove the whole resource if nothing is left, because empty
              // means "use all" and that is definitively not intended if only a
              // single resource is present.
              project.getBuild ().removeResource (aRes);

              if (verbose)
                getLog ().info ("Removed from project: " + _getResString (aRes));
            }
          }
          else
            if (aRes.getExcludes ().isEmpty ())
            {
              aRes.getExcludes ().add (sSearchName1);
              if (verbose)
                getLog ().info ("Excluding '" + sSearchName1 + "' from: " + _getResString (aRes));
            }
            else
            {
              if (verbose)
                getLog ().info ("  Unchanged Resource: " + _getResString (aRes));
            }
        }
      }

      // Add output file as a resource-file as the last action
      {
        final Resource aResource = new Resource ();
        aResource.setDirectory (fTarget.getParentFile ().getAbsolutePath ());
        aResource.addInclude (fTarget.getName ());
        aResource.setFiltering (false);
        aResource.setTargetPath ("META-INF/");
        project.addResource (aResource);
      }

      if (verbose)
        _listResources ("After modification");
    }
  }
}
