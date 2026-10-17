# sciologer
Visualization system for exploring relationships in data

Version 0.1

Created by Michael Bales in 2008

## Requirements
* Tomcat
* Icons needed for the visualization may be downloaded from the `drawing` folder

## Implementing Sciologer

### To implement Sciologer locally, do the following:
*	Copy the `e` folder to your machine in the location of your choosing
*	Modify `file_settings.java` to point to this folder
*	Install Tomcat
*	Configure Eclipse for Tomcat
*	Find the file `httpd.conf`, for example at `/etc/apache2` and change the `DocumentRoot` to match the local folder where `SciologerAdv.html` is located
*	Restart Tomcat through Eclipse
*	Install Google Earth (may need to use sudo if installing on Linux)
*	Point browser to http://localhost/SciologerSVN/SciologerAdv.html
*	Run test query: `F1275` (This query returns only four records from PubMed as of July 2017. Other test queries: `neuroligins`; `neuregulins`)
*	Open the resulting KML file (the browser should tell you where it is)

### Troubleshooting
*	Make sure `CVResults` is set to upload file to the user
*	Verify that `web.xml` is configured properly
*	Check permissions in the `e` subfolders
*	If nodes are too large or too small, scaling may be modified in `eleph1nt.java`

### Additional notes
*	Try several test queries to verify that advanced search is working properly
* When running on a server, to make modifications to Sciologer it is sufficient to upload modified class files. These are located at: `SciologerSVN\build\classes\bales`

### Descriptions of subfolders of the `e` folder
*	`a`: adjacencies
*	`ab`: abstracts
*	`act`: all corpus tokens
*	`d`: parsed
*	`f`: word frequencies
*	`fc`: full citations
*	`fc_util`: postprocessed full citations
*	`k`: kmz builder utility folder; stores files needed for building zipped kmz files which include the kml file, ground overlay, and relevant icons, models, photos, or drawings
*	`li`: links
*	`np`: noun phrases
*	`o`: output
*	`p`: node positions
*	`pd`: node positions sorted by degree
*	`pt`: node positions sorted by type
*	`q`: queries for batch mode
*	`stemmed`: stemmer output
*	`tok`: tokenizer output
*	`tok2`: postprocessed tokenizer output
*	`u`: umls utility file

### Order of operations

* `Controll3r` runs Entrez E-Utilities and generates files in: `fc` (full citations)
* Optional -- if including tags
 * `tok`=tokens -- A utility folder for storing intermediate tokenizations
 * `tok2`=tokens2 -- A utility folder for storing intermediate tokenizations
 * `act`=all corpus tokens -- A list of common token frequencies for the entire corpus from which the search results are drawn, in the format `token|frequency`.
 * `ti`=token inclusions -- A utility folder for storing data on whether tokens are to be included
* Optional -- if including UMLS lexical variants
 * `u`=umls lexical variants -- a folder for storing the file `umls_with_lexical_variants_all.txt`
 * (This file must be included on server upon implementation of Sciologer.)
* `Controll3r` runs `procfc` which generates files in `a` (adjacencies), using the typepair list format:
`net "np_mutation" "np_gene FOXP2" 1`
* `Controll3r` runs `ccvisu` which generates files in `p` (positions) using this format:
`-28.165995	14.799589	1.2726835	5.0	new brain imaging study	65280	false`
* `Controll3r` runs `NodeSortByType` which generates files in `pt` (positions sorted by type)
* `Controll3r` runs `NodeSortByDegree` which generates files in `pd` (positions sorted by degree)
* `Controll3r` runs `PrepLinks` which generates files in `li` (links)
* `Controll3r` runs `eleph1nt` which generates files in `o` (output)
