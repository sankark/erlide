package org.erlide.util;

import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;
import org.erlide.utils.ErlLogger;

public abstract class MessageReporter {
    public enum ReporterPosition {
        CENTER, CORNER, MODAL
    }

    public enum MessageType {
        ERROR, WARNING, INFO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    abstract public void displayMessage(MessageType type, String message,
            ReporterPosition style);

    public static void showError(final String message) {
        show(MessageType.ERROR, message, ReporterPosition.CORNER);
    }

    public static void showWarning(final String message) {
        show(MessageType.WARNING, message, ReporterPosition.CORNER);
    }

    public static void showInfo(final String message) {
        show(MessageType.INFO, message, ReporterPosition.CORNER);
    }

    public static void showError(final String message,
            final ReporterPosition style) {
        show(MessageType.ERROR, message, style);
    }

    public static void showWarning(final String message,
            final ReporterPosition style) {
        show(MessageType.WARNING, message, style);
    }

    public static void showInfo(final String message,
            final ReporterPosition style) {
        show(MessageType.INFO, message, style);
    }

    public static void show(final MessageType type, final String message,
            final ReporterPosition style) {
        final List<MessageReporter> reporters = getAllImplementors();
        for (final MessageReporter reporter : reporters) {
            reporter.displayMessage(type, message, style);
        }
        ErlLogger.info(type + "::: " + message);
    }

    private static List<MessageReporter> getAllImplementors() {
        final List<MessageReporter> result = ExtensionUtils.getExtensions(
                "org.erlide.util_eclipse.messageReporter",
                MessageReporter.class);
        return result;
    }

    public static IConfigurationElement[] getMessageReporterConfigurationElements() {
        final IExtensionRegistry reg = RegistryFactory.getRegistry();
        return reg.getConfigurationElementsFor("org.erlide.util_eclipse",
                "messageReporter");
    }

}
