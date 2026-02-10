sap.ui.define([
    'sap/base/strings/formatMessage',
    'io/simplifier/ui5/adminui/Util',
    'io/simplifier/ui5/adminui/modules/AppController',
    'sap/ui/model/json/JSONModel',
    'sap/ui/model/resource/ResourceModel',
    'io/simplifier/ui5/adminui/Ajax',
    'sap/ui/thirdparty/jquery',
    'sap/m/Label',
    'sap/m/InstanceManager',
    'sap/ui/core/HTML',
    'sap/ui/core/BusyIndicator',
    'sap/ui/model/Sorter',
    'sap/ui/model/Filter',
    'sap/ui/model/FilterOperator',
], function(formatMessage, Util, Controller, JSONModel, ResourceModel, Ajax, jQuery, Label, InstanceManager, HTML, BusyIndicator, Sorter, Filter, FilterOperator) {
    "use strict";

    /*
     * @author Daniel Bieberstein
     * pdfGeneratorController
     * The controller for the PDF Generator Plugin
     * Handels all ze stuff \o/
     */
    var pdfGeneratorController = Controller.extend("io.simplifier.ui5.plugin.pdfPlugin.adminui.pdfGenerator", {

        onInit: function() {
            Controller.prototype.onInit.apply(this, arguments);  // call super.onInit()

            // Load mustache
            var mustacheUrl = "/client/1.0/PLUGINASSET/pdfPlugin/adminui/mustache.min.js";
            jQuery.getScript(mustacheUrl, function(){});

            // Load LESS
            var lessUrl = "/client/1.0/PLUGINASSET/pdfPlugin/adminui/less.min.js";
            jQuery.getScript(lessUrl, function(){});

            var oData = {
                "templates": [],
                "fields": [],
                "current": {
                    enabled: false,
                    loaded: false,
                    inserted: false,
                    templateName: "",
                    content: "",
                    css: "",
                    json: ""
                }
            };
            var oModel = new JSONModel(oData);
            this.getView().setModel(oModel);

            // I18n for plugin view
            var i18nModel = new ResourceModel({
                bundleUrl : sap.ui.require.toUrl("io/simplifier/ui5/plugin/pdfPlugin/adminui/i18n/i18n.properties")
            });
            this.getView().setModel(i18nModel, "i18n_pdfplugin");

            this.refreshTemplates();
            this.clearPreview();
        },

        createSkeleton: function() {

            var html = 	"<h2>Welcome to the PDF Generator</h2> \n " +
                "<p>You can switch between <span class='highlight'>HTML</span> and <span class='highlight'>CSS</span> by clicking the respective buttons on top of this textarea</p>\n" +
                "<p>The preview will be generated automatically</p>\n" +
                "<p>Here are some of the features:</p> \n" +
                "<ul>\n  <li>Livepreview</li>\n  <li>Mustachefields</li>\n  <li>HTML and CSS manipulation</li>\n  <li>Add, edit, export templates</li>\n</ul>";

            var css = 	"h2{\n    color:grey;\n}\n" +
                ".highlight{\n    color:#1c98d6;\n    font-weight:bold;\n}"

            var json =  JSON.stringify({
                'key' : 'value',
                'name' : 'Hello World'
            }, null, '\t');

            var oModel = this.getView().getModel();
            oModel.setProperty("/current/content", html);
            oModel.setProperty("/current/header", "");
            oModel.setProperty("/current/footer", "");
            oModel.setProperty("/current/css", css);
            oModel.setProperty("/current/json", json);
        },

        /*
         * @author Daniel Bieberstein
         * onTemplatePress hides the fields panel and shows the templates panel
         */
        onTemplatePress:function(){
            this.getView().byId("fieldsContent").setVisible(false);
            this.getView().byId("templatesContent").setVisible(true);
        },
        /*
         * @author Daniel Bieberstein
         * onFieldsPress hides the templates panel and shows the fields panel
         */
        onFieldsPress: function(){
            this.getView().byId("templatesContent").setVisible(false);
            this.getView().byId("fieldsContent").setVisible(true);
        },
        /*
         * @author Daniel Bieberstein
         * onHTMLPress hides the css textarea and shows the html textarea
         */
        onHTMLPress: function(){
            this.getView().byId("htmlContent").setVisible(true);
            this.getView().byId("headerContent").setVisible(false);
            this.getView().byId("footerContent").setVisible(false);
            this.getView().byId("cssContent").setVisible(false);
            this.getView().byId("jsonContent").setVisible(false);
        },
        onHeaderPress: function() {
            this.getView().byId("htmlContent").setVisible(false);
            this.getView().byId("headerContent").setVisible(true);
            this.getView().byId("footerContent").setVisible(false);
            this.getView().byId("cssContent").setVisible(false);
            this.getView().byId("jsonContent").setVisible(false);
        },
        onFooterPress: function() {
            this.getView().byId("htmlContent").setVisible(false);
            this.getView().byId("headerContent").setVisible(false);
            this.getView().byId("footerContent").setVisible(true);
            this.getView().byId("cssContent").setVisible(false);
            this.getView().byId("jsonContent").setVisible(false);
        },
        /*
         * @author Daniel Bieberstein
         * onCSSPress hides the html textarea and shows the css textarea
         */
        onCSSPress: function(){
            this.getView().byId("htmlContent").setVisible(false);
            this.getView().byId("headerContent").setVisible(false);
            this.getView().byId("footerContent").setVisible(false);
            this.getView().byId("cssContent").setVisible(true);
            this.getView().byId("jsonContent").setVisible(false);
        },

        onJSONPress: function(){
            this.getView().byId("htmlContent").setVisible(false);
            this.getView().byId("headerContent").setVisible(false);
            this.getView().byId("footerContent").setVisible(false);
            this.getView().byId("cssContent").setVisible(false);
            this.getView().byId("jsonContent").setVisible(true);
        },

        clearPreview: function() {
            this.getView().byId("previewPanel").destroyContent();
            var oModel = this.getView().getModel();
            oModel.setProperty("/fields", []);
        },

        /*
         * @author Daniel Bieberstein
         * @author Christian Simon
         * onGeneratePreview generates the preview using the value of both, css and html, textareas
         */
        onGeneratePreview: function (){
            // TODO: Add Debouncer, to wait until user stops typing?
            var html = "<main>" + this.getView().byId("htmlContent").getProperty("editorValue") + "</main>";
            var header = "<header>" +  this.getView().byId("headerContent").getProperty("editorValue") + "</header>";
            var footer = "<footer>" + this.getView().byId("footerContent").getProperty("editorValue") + "</footer>";
            var completeHtml = header + html + footer;
            var css = this.getView().byId("cssContent").getProperty("editorValue");
            var jsonData = this.getView().byId("jsonContent").getProperty("editorValue");
            this.getView().byId("previewPanel").destroyContent();

            var json = {};
            var jsonValid = true;
            if (jsonData !== "") {
                try {
                    json = JSON.parse(jsonData);
                } catch (e) {
                    jsonValid = false;
                }
            }

            // Update fields tree
            var oModel = this.getView().getModel();
            var fields = [];
            if (jsonValid) {
                jQuery.each(json, function(key, value) {
                    fields.push({'name': key});
                });
            }
            oModel.setProperty("/fields", fields);

            var code = "";
            var mustacheValid = true;
            try {
                code = Mustache.render(completeHtml, json);
            } catch (e) {
                mustacheValid = false;
            }

            var previewSelector = '#' + this.getView().byId("previewPanel").sId + " > .sapMPanelContent";

            less.render(previewSelector + " { " + css + " }", (function(e, output) {

                var lessValid = true;
                var less = "";
                if (typeof output !== "undefined" && typeof output.css !== "undefined") {
                    less = output.css;
                } else {
                    lessValid = false;
                }

                var content;
                if (!mustacheValid) {
                    content = new Label({
                        text: "Invalid HTML template"
                    });
                } else if (!jsonValid) {
                    content = new Label({
                        text: "Invalid JSON data"
                    });
                } else if (!lessValid) {
                    content = new Label({
                        text: "Invalid Stylesheet data"
                    });
                } else {
                    content = new HTML().setContent(
                        '<style type="text/css">' + less + '</style><div>' + code + '</div>');
                }
                this.getView().byId("previewPanel").addContent(content);
            }).bind(this));
        },

        onSaveTemplate: function(){
            var oModel = this.getView().getModel();
            var inserted = oModel.getProperty("/current/inserted") === true ? true : false;
            if (inserted) {
                this.editTemplate();
            } else {
                this.insertTemplate();
            }
        },

        /**
         * calls the onSaveTemplate function by pressing cmd + s
         */
        onShortcutSave: function () {
            this.onSaveTemplate();
        },

        /**
         * overwritten function from AppController to check if saving is allowed
         * @returns {any}
         */
        allowSave: function () {
            const oModel = this.getView().getModel();
            const bSaveAllowed = oModel.getProperty("/current/enabled") && oModel.getProperty("/current/loaded");
            return bSaveAllowed;
        },

        onCreateNewTemplate: function(){
            this.startNewTemplate();
        },

        onDeleteTemplate: function(){
            var sTemplate = this.getView().getModel().getProperty("/current/templateName");
            Util.showConfirmationDialog(
                formatMessage("Do you really wish to delete template ''{0}''?", sTemplate),
                "Delete template",
                function() {
                    this.deleteTemplate();
                },
                undefined,
                this,
                {
                    icon : "sap-icon://delete",
                    state : "Warning"
                }
            );
        },

        /**
         * calls the onDeleteTemplate function by pressing "del" on the keyboard
         */
        onKeyboardDelete: function () {
            this.onDeleteTemplate();
        },

        /**
         * overwritten function from AppController to check if deleting is allowed
         * @returns {any}
         */
        allowDelete: function () {
            const oModel = this.getView().getModel();
            const bDeleteAllowed = oModel.getProperty("/current/enabled") && oModel.getProperty("/current/inserted");
            const bListNotEmpty = oModel.getProperty("/templates/length") !== 0;
            const bNoDialogOpen = InstanceManager.getOpenDialogs().length === 0;
            return bDeleteAllowed && bListNotEmpty && bNoDialogOpen;
        },

        onTemplateSelected: function(oEvent) {
            var templateName = oEvent.getParameter("listItem").getBindingContext().getObject().name;
            this.loadTemplate(templateName);
        },

        initTemplate: function() {
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", false);
            oModel.setProperty("/current/inserted", false);
            oModel.setProperty("/current/loaded", false);
            oModel.setProperty("/current/templateName", "");
            oModel.setProperty("/current/content", "");
            oModel.setProperty("/current/css", "");
            oModel.setProperty("/current/json", "");
        },

        refreshTemplates: function() {
            this.apiListTemplates(this.onTemplateListRefreshed, this.onTemplateListRefreshFailed);
        },

        onTemplateListRefreshed: function(data) {
            var oModel = this.getView().getModel();
            var templates = [];
            if (!data.success) {
                // no actual successful result
                this.onTemplateListRefreshFailed(data);
                return;
            }
            jQuery.each(data.templates, function() {
                templates.push({name: this});
            });
            oModel.setProperty("/templates", templates);
            oModel.setProperty("/current/enabled", true);

            var oList = this.getView().byId("templatesTree");
            if (oList.getBinding("items")) {
                oList.getBinding("items").sort(new Sorter("name"));
            }
        },

        onTemplateListRefreshFailed: function(error) {
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Refreshing Templates", detail.msgText);
        },

        loadTemplate: function(name) {
            this.initTemplate();
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/templateName", name);
            this.apiFetchTemplate(name, this.onTemplateLoaded, this.onTemplateLoadFailed);
        },

        onTemplateLoaded: function(data) {
            if (!data.success) {
                // no actual successful result
                this.onTemplateLoadFailed(data);
                return;
            }
            var content = this._base64Decode(data.template.template);
            var header = data.template.header ? this._base64Decode(data.template.header) : "";
            var footer = data.template.footer ? this._base64Decode(data.template.footer) : "";
            var css = this._base64Decode(data.template.stylesheet);
            var json = this._base64Decode(data.template.previewJson);
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
            oModel.setProperty("/current/loaded", true);
            oModel.setProperty("/current/inserted", true);
            oModel.setProperty("/current/content", content);
            oModel.setProperty("/current/header", header);
            oModel.setProperty("/current/footer", footer);
            oModel.setProperty("/current/css", css);
            oModel.setProperty("/current/json", json);
            this.onGeneratePreview();
        },

        onTemplateLoadFailed: function(error) {
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Loading Template", detail);
            var oModel = this.getView().getModel();
            this.initTemplate();
            oModel.setProperty("/current/enabled", true);
        },

        startNewTemplate: function() {
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
            oModel.setProperty("/current/loaded", true);
            oModel.setProperty("/current/inserted", false);
            oModel.setProperty("/current/templateName", "newTemplate");
            this.createSkeleton();

            // Clear selection from tree
            var oTemplateList = this.getView().byId('templatesTree');
            oTemplateList.removeSelections();

            this.onGeneratePreview();
        },

        insertTemplate: function() {
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", false);
            var templateName = oModel.getProperty("/current/templateName");
            var templateContent = oModel.getProperty("/current/content");
            var templateHeader = oModel.getProperty("/current/header");
            var templateFooter = oModel.getProperty("/current/footer");
            var stylesheet = oModel.getProperty("/current/css");
            var json = oModel.getProperty("/current/json");
            this.apiInsertTemplate(templateName, templateContent, templateHeader, templateFooter,
                stylesheet, json, this.onTemplateInserted, this.onTemplateInsertFailed);
        },

        onTemplateInserted: function(data) {
            if (!data.success) {
                this.onTemplateInsertFailed(data);
                return;
            }
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/inserted", true);
            this.apiListTemplates(this.onTemplateListRefreshedAfterInsert, this.onTemplateListRefreshFailed);
        },

        onTemplateListRefreshedAfterInsert: function(data) {
            var oModel = this.getView().getModel();
            var templates = [];
            if (!data.success) {
                // no actual successful result
                this.onTemplateListRefreshFailed(data);
                return;
            }
            jQuery.each(data.templates, function() {
                templates.push({name: this});
            });
            oModel.setProperty("/templates", templates);
            oModel.setProperty("/current/enabled", true);

            // Select inserted template in list
            var templateName = oModel.getProperty("/current/templateName");
            var oTemplateList = this.getView().byId('templatesTree');
            jQuery.each(oTemplateList.getItems(), function() {
                var oNode = this;
                if (oNode.prop('title') === templateName) {
                    oTemplateList.setSelectedItem(oNode);
                }
            });
        },

        onTemplateInsertFailed: function(error) {
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Inserting Template", detail);
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
        },

        editTemplate: function() {
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", false);
            var templateName = oModel.getProperty("/current/templateName");
            var templateHeader = oModel.getProperty("/current/header");
            var templateFooter = oModel.getProperty("/current/footer");
            var templateContent = oModel.getProperty("/current/content");
            var stylesheet = oModel.getProperty("/current/css");
            var json = oModel.getProperty("/current/json");
            this.apiEditTemplate(templateName, templateContent, templateHeader, templateFooter,
                stylesheet, json, this.onTemplateEdited, this.onTemplateEditFailed);
        },

        onTemplateEdited: function(data) {
            if (!data.success) {
                this.onTemplateEditFailed(data);
                return;
            }
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
        },

        onTemplateEditFailed: function(error) {
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Editing Template", detail);
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
        },

        deleteTemplate: function() {
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", false);
            var templateName = oModel.getProperty("/current/templateName");
            this.apiDeleteTemplate(templateName, this.onTemplateDeleted, this.onTemplateDeleteFailed);
        },

        onTemplateDeleted: function(data) {
            if (!data.success) {
                this.onTemplateDeleteFailed(data);
                return;
            }
            this.initTemplate();
            this.clearPreview();
            this.apiListTemplates(this.onTemplateListRefreshedAfterDelete, this.onTemplateListRefreshFailed);
        },

        onTemplateListRefreshedAfterDelete: function(data) {
            var oModel = this.getView().getModel();
            var templates = [];
            if (!data.success) {
                // no actual successful result
                this.onTemplateListRefreshFailed(data);
                return;
            }
            jQuery.each(data.templates, function() {
                templates.push({name: this});
            });
            oModel.setProperty("/templates", templates);
            oModel.setProperty("/current/enabled", true);

            // Select inserted template in list
            var oTemplateList = this.getView().byId('templatesTree');
            oTemplateList.removeSelections();
        },

        onTemplateDeleteFailed: function(error) {
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Deleting Template", detail);
            var oModel = this.getView().getModel();
            oModel.setProperty("/current/enabled", true);
        },

        onPdfPreview: function() {
            BusyIndicator.show();
            var oModel = this.getView().getModel();
            var jsonData = oModel.getProperty("/current/json");
            var json = {};
            if (jsonData !== "") {
                try {
                    json = JSON.parse(jsonData);
                } catch (e) {
                }
            }
            var session = "preview";
            var sessionData = JSON.stringify(json);
            var sessionKey = "sessiondata/" + session;
            var templateName = oModel.getProperty("/current/templateName");
            console.log("Try Generate with " + templateName);

            this.apiPutKeyValueStore(sessionKey, sessionData, function(putKvData) {
                if (!putKvData.success) {
                    this.onPdfPreviewError(putKvData);
                    return;
                }
                this.apiGeneratePdf(templateName, session, function(genPdfData) {
                    if (!genPdfData.success) {
                        this.onPdfPreviewError(genPdfData);
                        return;
                    }
                    this.previewJobId = genPdfData.value.jobId;
                    this.genPdfWaitTries = 20;
                    setTimeout(this.onWaitForGeneratedPdf.bind(this), 1000);
                }, this.onPdfPreviewError);
            }, this.onPdfPreviewError);
        },

        onWaitForGeneratedPdf: function() {
            var counter = this.genPdfWaitTries;
            if (!counter || counter <= 0) {
                console.log("Wait without success ...");
                BusyIndicator.hide();
                return;
            }
            this.genPdfWaitTries = counter - 1;

            console.log("Wait " + counter + " ...");
            var key = "pdf/" + this.previewJobId + ".pdf";
            this.apiGetKeyValueStore(key, function(data) {
                if (!data.success) {
                    this.onWaitForGeneratedPdfError(data);
                    return;
                }
                console.log(data.result);
                BusyIndicator.hide();

                var pdfPreview = window.open("/client/1.0/PLUGINASSET/pdfPlugin/adminui/pdf.html", "_blank");
                pdfPreview.onload = function() {
                    pdfPreview.postMessage(data.result);
                };

            }, this.onWaitForGeneratedPdfError);
        },

        onWaitForGeneratedPdfError: function(error) {
            console.log("Not found. Retrying ...");
            setTimeout(this.onWaitForGeneratedPdf.bind(this), 1000);
        },

        onPdfPreviewError: function(error) {
            BusyIndicator.hide();
            var detail = error;
            if (error.message) {
                detail = error.message;
            }
            this.onDisplayError("Error Generating Preview PDF", detail);
        },

        onDisplayError: function(message, detail) {
            Util.showErrorDialog({sMsg: message, oDetails: detail});
        },

        /*
         * PDF Plugin API
         */

        apiGetUsername: function() {
            // TODO: Get Username from Auth
            return "admin"
        },

        apiGetPassword: function() {
            // TODO: Get Username from Auth
            return "admin"
        },

        apiRequest: function(urlSuffix, data, callback, errorCallback) {
            var url = "/client/1.0/PLUGIN/pdfPlugin/" + urlSuffix;
            var request = {
                'username' : this.apiGetUsername(),
                'password' : this.apiGetPassword(),
                'method' : 'POST',
                'data' : JSON.stringify(data),
                'dataType' : 'json',
                'success' : function(responseData, textStatus, xhr) {
                    callback.call(this, responseData);
                },
                'error' : function(xhr, textStatus, error) {
                    errorCallback.call(this, error);
                },
                context: this
            };
            Ajax.ajaxPost(url, request.success, request.error, {},
                request.context, request.data, request.dataType
            );
            //jQuery.ajax(url, request);
        },

        apiListTemplates: function(callback, errorCallback) {
            this.apiRequest("adminTemplateList", {}, callback, errorCallback);
        },

        apiFetchTemplate: function(name, callback, errorCallback) {
            var data = {
                'name': name
            };
            this.apiRequest("adminTemplateFetch", data, callback, errorCallback);
        },

        apiEditTemplate: function(name, content, header, footer, stylesheet, previewJson, callback, errorCallback) {
            this._apiEditOrInsertTemplate("adminTemplateEdit", name, content, header, footer, stylesheet, previewJson, callback, errorCallback);
        },

        apiInsertTemplate: function(name, content, header, footer, stylesheet, previewJson, callback, errorCallback) {
            this._apiEditOrInsertTemplate("adminTemplateAdd", name, content, header, footer, stylesheet, previewJson, callback, errorCallback);
        },

        _apiEditOrInsertTemplate(slotName, name, content, header, footer, stylesheet, previewJson, callback, errorCallback) {
            var data = {
                'name': name,
                'data': this._base64Encode(content),
                'header': this._base64Encode(header),
                'footer': this._base64Encode(footer),
                'stylesheet': this._base64Encode(stylesheet),
                'previewJson': this._base64Encode(previewJson)
            };

            this.apiRequest(slotName, data, callback, errorCallback)
        },

        apiDeleteTemplate: function(name, callback, errorCallback) {
            var data = {
                'name': name
            };
            this.apiRequest("adminTemplateDelete", data, callback, errorCallback);
        },

        apiGeneratePdf: function(template, session, callback, errorCallback) {
            var data = {
                'template': template,
                'session': session
            };
            this.apiRequest("generatePdf", data, callback, errorCallback);
        },

        apiRequestKeyValueStore: function(urlSuffix, data, callback, errorCallback) {
            var url = "/client/1.0/PLUGIN/keyValueStorePlugin/" + urlSuffix;
            var request = {
                'username' : this.apiGetUsername(),
                'password' : this.apiGetPassword(),
                'method' : 'POST',
                'data' : JSON.stringify(data),
                'dataType' : 'json',
                'success' : function(responseData, textStatus, xhr) {
                    callback.call(this, responseData);
                },
                'error' : function(xhr, textStatus, error) {
                    errorCallback.call(this, error);
                },
                context: this
            };
            Ajax.ajaxPost(url, request.success, request.error, {},
                request.context, request.data, request.dataType
            );
            //jQuery.ajax(url, request);
        },

        apiPutKeyValueStore: function(key, content, callback, errorCallback) {
            var data = {
                'key': key,
                'content': this._base64Encode(content)
            };
            this.apiRequestKeyValueStore("puthttp", data, callback, errorCallback);
        },

        apiGetKeyValueStore: function(key, callback, errorCallback) {
            var data = {
                'key': key
            };
            this.apiRequestKeyValueStore("gethttp", data, callback, errorCallback);
        },

        onSearchTemplate : function(oEvent) {
            var oList = this.getView().byId("templatesTree");

            var aFilters = [];
            var sQuery = oEvent.getParameter("newValue");
            if (sQuery && sQuery.length > 0) {
                aFilters.push(new Filter("name", FilterOperator.Contains, sQuery));
            }
            oList.getBinding("items").filter(aFilters);
        },

        _base64Encode(input) {
            var result = "";
            try {
                result = btoa(encodeURIComponent(input).replace(/%([0-9A-F]{2})/g, function (match, p1) {
                    return String.fromCharCode(parseInt(p1, 16))
                }));
            } catch (e) {
                result = btoa(input);
            }
            return result;
        },

        _base64Decode(input) {
            var result = "";
            try {
                result = decodeURIComponent(Array.prototype.map.call(atob(input), function (c) {
                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
                }).join(''));
            } catch (e) {
                result = atob(input);
            }
            return result;
        }

    });

    return pdfGeneratorController;
});
