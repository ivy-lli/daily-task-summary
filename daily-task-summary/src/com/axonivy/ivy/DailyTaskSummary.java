package com.axonivy.ivy;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.mail.MailClient;
import ch.ivyteam.ivy.mail.MailMessage;
import ch.ivyteam.ivy.security.IUser;
import ch.ivyteam.ivy.workflow.ITask;
import ch.ivyteam.ivy.workflow.query.TaskQuery;
import ch.ivyteam.ivy.workflow.task.TaskBusinessState;

public class DailyTaskSummary {

  public static void send() {
    try (MailClient mail = MailClient.create()) {
      for (IUser user : Ivy.security().users().paged()) {
        String address = user.getEMailAddress();
        if (address == null || address.isBlank()) {
          continue;
        }

        TaskQuery query = TaskQuery.create()
            .where().businessState().isIn(TaskBusinessState.OPEN, TaskBusinessState.IN_PROGRESS)
            .and().canWorkOn(user);
        StringBuilder body = new StringBuilder("Your open tasks:\n\n");
        int count = 0;
        for (ITask task : query.executor().resultsPaged()) {
          body.append("- ").append(task.getName()).append(" (#")
              .append(task.getId()).append(")\n");
          count++;
        }
        if (count == 0) {
          continue;
        }

        mail.send(MailMessage.create()
            .to(address)
            .subject("Daily task summary (" + count + ")")
            .textContent(body.toString())
            .toMailMessage());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Could not send daily task summaries", exception);
    }
  }
}