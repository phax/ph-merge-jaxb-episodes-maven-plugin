/**
 * Copyright (C) 2021 Philip Helger (www.helger.com)
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
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.commons.collection.ArrayHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.datetime.PDTFactory;
import com.helger.commons.io.file.SimpleFileIO;
import com.helger.xml.NodeListIterator;
import com.helger.xml.XMLFactory;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.write.XMLWriter;

/**
 * @author Philip Helger
 * @description Merge all mentioned JAXB episode files "sun-jaxb.episode" into a
 *              single one
 */
@Mojo (name = "merge-jaxb-episodes", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public final class MergeJaxbEpisodesMojo extends AbstractMojo
{
  private static final String OUTPUT_FOLDER = "merged-jaxb-episode";
  private static final String FILENAME = "sun-jaxb.episode";

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

  public void setBaseDirectory (@Nonnull final File aDir)
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

  @Nonnull
  private static String _getResString (@Nonnull final Resource aRes)
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

  private void _listResources (@Nonnull final String sReason)
  {
    final List <Resource> aRess = project.getBuild ().getResources ();
    getLog ().info ("Maven resources [" + aRess.size () + "] - " + sReason);
    for (final Resource aRes : aRess)
      getLog ().info ("  Resource: " + _getResString (aRes));
  }

  private byte [] _mergeAsXML (final ICommonsList <File> aMatches) throws MojoExecutionException
  {
    if (verbose)
      getLog ().info ("Merging " + aMatches.size () + " files using XML parsing/cloning");

    final Document aTargetDoc = XMLFactory.newDocument ();

    // <bindings xmlns="http://java.sun.com/xml/ns/jaxb" if-exists="true"
    // version="2.1">
    final Element aTargetRoot = (Element) aTargetDoc.appendChild (aTargetDoc.createElementNS ("http://java.sun.com/xml/ns/jaxb",
                                                                                              "bindings"));
    aTargetRoot.setAttribute ("if-exists", "true");
    aTargetRoot.setAttribute ("version", "2.1");

    final StringBuilder aCommentSB = new StringBuilder ();
    aCommentSB.append ("This file was automatically created by ph-merge-jaxb-episodes-maven-plugin - do NOT edit.");
    aCommentSB.append ("\nThis file was made up of:");
    for (final File aFile : aMatches)
      aCommentSB.append ("\n  ").append (aFile.getPath ());
    aCommentSB.append ("\n\nThis file was written at ").append (PDTFactory.getCurrentZonedDateTime ().toString ());
    aTargetRoot.appendChild (aTargetDoc.createComment (aCommentSB.toString ()));

    for (final File aFile : aMatches)
    {
      if (verbose)
        getLog ().info ("Parsing XML file '" + aFile.getPath () + "'");

      final Document aExistingDoc = DOMReader.readXMLDOM (aFile);
      if (aExistingDoc == null)
        throw new MojoExecutionException ("The file '" + aFile.getAbsolutePath () + "' is invalid XML");

      final Element aExistingRoot = aExistingDoc.getDocumentElement ();
      if (!"bindings".equals (aExistingRoot.getLocalName ()))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected element name)");
      if (!"http://java.sun.com/xml/ns/jaxb".equals (aExistingRoot.getNamespaceURI ()))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected namespace URI)");

      // Copy to target document
      for (final Node aChildNode : NodeListIterator.createChildNodeIterator (aExistingRoot))
      {
        aTargetRoot.appendChild (aTargetDoc.adoptNode (aChildNode.cloneNode (true)));
      }
    }

    return XMLWriter.getNodeAsBytes (aTargetDoc);
  }

  public void execute () throws MojoExecutionException
  {
    if (baseDirectory == null)
      throw new MojoExecutionException ("No baseDirectory specified!");
    if (ArrayHelper.isEmpty (episodeFiles))
      throw new MojoExecutionException ("No episode file is specified");

    final ICommonsList <File> aMatches = new CommonsArrayList <> ();

    if (false)
    {
      // Scan all existing resources
      for (final Resource aRes : project.getBuild ().getResources ())
      {
        final DirectoryScanner aScanner = new DirectoryScanner ();
        aScanner.setBasedir (aRes.getDirectory ());
        aScanner.setIncludes (aRes.getIncludes ().toArray (ArrayHelper.EMPTY_STRING_ARRAY));
        aScanner.setExcludes (aRes.getExcludes ().toArray (ArrayHelper.EMPTY_STRING_ARRAY));
        aScanner.setCaseSensitive (true);
        aScanner.scan ();

        final String [] aFilenames = aScanner.getIncludedFiles ();
        if (aFilenames != null)
          for (final String sFilename : aFilenames)
          {
            final File aFile = new File (aRes.getDirectory (), sFilename);

            if (sFilename.equals (FILENAME))
            {
              getLog ().info ("Found episode file to merge: " + aFile.getPath ());
              aMatches.add (aFile);
            }
          }
      }
    }
    else
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
      byte [] aTargetXML;
      aTargetXML = _mergeAsXML (aMatches);
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
