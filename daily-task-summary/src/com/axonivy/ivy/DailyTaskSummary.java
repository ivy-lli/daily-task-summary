package com.axonivy.ivy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.util.ByteArrayDataSource;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.mail.Attachment;
import ch.ivyteam.ivy.mail.MailClient;
import ch.ivyteam.ivy.mail.MailMessage;
import ch.ivyteam.ivy.security.IUser;
import ch.ivyteam.ivy.workflow.ITask;
import ch.ivyteam.ivy.workflow.query.TaskQuery;
import ch.ivyteam.ivy.workflow.task.TaskBusinessState;

public class DailyTaskSummary {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)\\}\\}");

  public static void send() {
    try (MailClient mail = MailClient.newMailClient()) {
      for (IUser user : Ivy.security().users().paged()) {
        String address = user.getEMailAddress();
        if (address == null || address.isBlank()) {
          continue;
        }

        TaskQuery query = TaskQuery.create()
            .where().businessState().isIn(TaskBusinessState.OPEN, TaskBusinessState.IN_PROGRESS)
            .and().canWorkOn(user);
        String rowTemplate = loadTemplate("DailyTaskRow.html");
        StringBuilder tasks = new StringBuilder();
        String logoUrl = null;
        int count = 0;
        for (ITask task : query.executor().resultsPaged()) {
          if (logoUrl == null) {
            String startUrl = task.getStartLink().getAbsolute();
            logoUrl = startUrl.substring(0, startUrl.indexOf("/pro/"))
                + "/faces/javax.faces.resource/logo_mail.png?ln=xpertivy-branding";
          }
          tasks.append(render(rowTemplate, Map.of(
              "NAME", escape(task.getName()),
              "ID", String.valueOf(task.getId()),
              "PRIORITY", escape(String.valueOf(task.getPriority())),
              "CREATED", formatDate(task.getStartTimestamp()),
              "EXPIRY", formatDate(task.getExpiryTimestamp()),
              "DESCRIPTION", escape(task.getDescription()),
              "START_URL", escape(task.getStartLinkEmbedded().getAbsolute()),
              "DETAIL_URL", escape(task.getDetailLink().getAbsolute()))));
          count++;
        }
        if (count == 0) {
          continue;
        }

        String body = render(loadTemplate("DailyTaskSummary.html"), Map.of(
            "TASK_COUNT", String.valueOf(count),
            "TASK_NOUN", count == 1 ? "task" : "tasks",
            "TASK_ROWS", tasks.toString()));
        var message = MailMessage.create()
            .to(address)
            .subject("Daily task summary (" + count + ")")
            .htmlContent(body);
        try (InputStream logo = URI.create(logoUrl).toURL().openStream()) {
          message.attachments(Attachment.create()
              .dataSource(new ByteArrayDataSource(logo, "image/png"))
              .filename("logo_mail.png")
              .dispositionInline()
              .contentId("daily-summary-logo")
              .toAttachment());
        }
        mail.send(message.toMailMessage());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Could not send daily task summaries", exception);
    }
  }

  private static String loadTemplate(String name) {
    try (InputStream stream = DailyTaskSummary.class.getResourceAsStream(name)) {
      if (stream == null) {
        throw new IllegalStateException("Missing mail template: " + name);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String render(String template, Map<String, String> values) {
    return PLACEHOLDER.matcher(template).replaceAll(match -> {
      String value = values.get(match.group(1));
      if (value == null) {
        throw new IllegalArgumentException("Unknown mail placeholder: " + match.group(1));
      }
      return Matcher.quoteReplacement(value);
    });
  }

  private static String formatDate(Date date) {
    return date == null ? "-" : DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
  }

  private static String escape(String value) {
    return value == null ? ""
        : value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }
}
