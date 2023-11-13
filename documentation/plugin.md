PDF Plugin
==========

About
-----

The PDF Plugin allows management (CRUD) of PDF templates in the HTML format, and the generation of PDFs from a template,
with an associated JSON map containing the parameters. 

Also the merging of the created PDFs with fixed PDF files from the key-value-store is supported.



Templates
---------

The templates can contain regular HTML, with variables in the [mustache](http://scalate.github.io/scalate/documentation/mustache.html) format.
Each template consists of the following data:
* HTML template with mustache-compatible variables
* Stylesheet in LESS format (will be compiled to CSS during PDF Printing)
* JSON data for preview function in admin UI

All referenced images and additional CSS stylesheets will be retrieved from the "assets" slot of the AppServer.

Administration
--------------

There is a configuration page in the [admin UI for Plugins](http://localhost:8080/UserInterface/#/Plugin) to configure and preview the templates. The configuration menu entry "Pdf Plugin" is only available, when the plugin is loaded.

REST interfaces
---------------

### List Templates

Operation to list all available template names.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/adminTemplateList`

Input: Nothing  
Output:

    {
        "success": true,
        "value": [
            "templatename1",
            "templatename2",
            "templatename3"
        ]
    }

The result value `value` contains a list of all template names available

### Add Template

Operation to insert a new template.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/adminTemplateAdd`

Input:

    {
        "name": "templatename",
        "data": "SGFsbG8gV2VsdA==\",
        "stylesheet": "SGFsbG8gV2VsdA==\",
        "previewJson": "SGFsbG8gV2VsdA==\"
    }

The input parameter `name` contains the unique name for the new template.  
The input parameter `data` contains the Base64-encoded content of the template HTML file.  
The input parameter `stylesheet` contains the Base64-encoded content of the template LESS Stylesheet (optional).  
The input parameter `previewJson` contains the Base64-encoded preview JSON-Data for AdminUI (optional).

Output:

If the template name is invalid:

    {
        "code": 1,
        "message": "template name invalid",
        "success": false
    }

If the template name already exists:

    {
        "code": 3,
        "message": "template name already in use",
        "success": false
    }

If the operation was successful:

    {
        "success": true
    }

### Fetch Template Content

Operation to fetch the content of a template.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/adminTemplateFetch`

Input:

    {
        "name": "templatename"
    }

The input parameter `name` contains the unique name of the target template.

Output:

If the template name is invalid:

    {
        "code": 1,
        "message": "template name invalid",
        "success": false
    }

If the template name does not exists:

    {
        "code": 2,
        "message": "template not existing",
        "success": false
    }

If the operation was successful:

    {
        "value": {
            "template": "aGVsbG8gd29ybGQ=",
            "stylesheet": "aGVsbG8gd29ybGQ=",
            "previewJson": "aGVsbG8gd29ybGQ="
        }
        "success": true
    }

The output value `value` contains an object with the following entries:
* `template` the Base64-encoded content of the HTML template
* `stylesheet` the Base64-encoded content of the LESS stylesheet
* `previewJson` the Base64-encoded preview JSON-Data for AdminUI

### Replace template content

Operation to replace the content of an existing template.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/adminTemplateEdit`

Input:

    {
        "name": "templatename",
        "data": "SGFsbG8gV2VsdA==\",
        "stylesheet": "SGFsbG8gV2VsdA==\",
        "previewJson": "SGFsbG8gV2VsdA==\"
    }

The input parameter `name` contains the unique name of an existing template.  
The input parameter `data` contains the new Base64-encoded content of the template file.  
The input parameter `stylesheet` contains the Base64-encoded content of the template LESS Stylesheet (optional, will be emptied if missing).  
The input parameter `previewJson` contains the Base64-encoded preview JSON-Data for AdminUI (optional, will be emptied if missing).

Output:

If the template name is invalid:

    {
        "code": 1,
        "message": "template name invalid",
        "success": false
    }

If the template name does not exists:

    {
        "code": 2,
        "message": "template not existing",
        "success": false
    }

If the operation was successful:

    {
        "success": true
    }

### Delete template

Operation to delete an existing template.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/adminTemplateDelete`

Input:

    {
        "name": "templatename"
    }

The input parameter `name` contains the unique name the template to delete.

Output:

If the template name is invalid:

    {
        "code": 1,
        "message": "template name invalid",
        "success": false
    }

If the template name does not exists:

    {
        "code": 2,
        "message": "template not existing",
        "success": false
    }

If the operation was successful:

    {
        "success": true
    }

### Generate PDF

Operation to start the template generation.

Method: `POST`  
URL: `/client/1.0/PLUGIN/pdfPlugin/generatePdf`

Input:

    {
        "template": "templatename",
        "session": "sessionname",
        "config": "{ \"orientation\": \"Portrait\", \"page-size\": \"A4\" }"
    }

The input parameter `template` contains the unique name of the template to generate the PDF from.  
The input parameter `session` contains the session name to fill in the session variables and PDFs/Images to merge.  
Session variables are taken as JSON data from the Key-Value-Store under the key `sessiondata/$session` (if existing).
Additional sources to merge the created PDF with can be put as JSON-Array in the Key-Value-Store under the key `merge/$session`.
All additional sources (PDF format or JPEG/PNG/GIF image) are retrieved from the Key-Value-Store under the key listed in the JSON array.

E.g. if `session = "testsession"`,

Key `sessiondata/testsession` in Key-Value-Store ->

    {
        "var1": "value1"
        "var2": [
            {
                "name": "Name 1",
                "age": 20
            },
            {
                "name": "Name 2",
                "age": 30
            }
        ]
    }

Key `merge/testsession` in Key-Value-Store ->

    ["attachment.pdf", "testimage"]

Key `attachment.pdf` in Key-Value-Store -> [binary PDF data]

Key `testimage` in Key-Value-Store -> [binary JPEG data]

The optional input parameter `config` can contain parameters for the PDF generation, encoded as JSON-String.
Available parameters are listed  in the sPDF class [PdfConfig](https://github.com/cloudify/sPDF/blob/master/src/main/scala/io/github/cloudify/scala.spdf/PdfConfig.scala),
where the keys in the config-JSON should correspond to the config parameter String (in paranthesis), e.g. "page-size" NOT "pageSize".

The images and stylesheets will be retrieved from the slot `assets` of the AppServer, if they have a relative URL.

Output:

If the template name is invalid:

    {
        "code": 1,
        "message": "template name invalid",
        "success": false
    }

If the template name does not exists:

    {
        "code": 2,
        "message": "template not existing",
        "success": false
    }

If the operation was successful:

    {
        "value": {
            "jobId": "59d74ce8-01ba-4339-8092-26fa10517854"
        },
        "success": true
    }

The returned `jobId` will be the key under which the result of the PDF generation will be published.
As the PDf generation job can take some time to run, the response with the generated jobId will be returned immediately, which the job is being processed.
After the job has finished (or terminated with an error), the result will be published as binary PDF in the Key-Value-Store under the key `pdf/$jobId.pdf` (if successful), or as Error message under the key `pdf/$jobId.log`. Due to the asynchronous execution, the only way to check if the PDF generation finished, is to check the Key-Value-Store for these two keys.