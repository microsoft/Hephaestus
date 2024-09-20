package com.hephaestus.models;

import java.io.Serializable;
import java.util.List;

public class FhirImportRequest implements Serializable {
    private String resourceType;
    private Parameter[] parameter;

    public static class Parameter {
        private String name;
        private String valueString;
        private Part[] part;

        public static class Part {
            private String name;
            private String valueUri;

            // Getters and Setters
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getValueUri() {
                return valueUri;
            }

            public void setValueUri(String valueUri) {
                this.valueUri = valueUri;
            }
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValueString() {
            return valueString;
        }

        public void setValueString(String valueString) {
            this.valueString = valueString;
        }

        public Part[] getPart() {
            return part;
        }

        public void setPart(Part[] part) {
            this.part = part;
        }
    }

    // Getters and Setters
    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Parameter[] getParameter() {
        return parameter;
    }

    public void setParameter(Parameter[] parameter) {
        this.parameter = parameter;
    }    
}
