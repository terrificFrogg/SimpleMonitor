module simple.monitor {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.apache.logging.log4j;

    exports org.monitor.model to com.fasterxml.jackson.databind;
    opens org.monitor;
}