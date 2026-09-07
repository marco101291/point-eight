package com.pointeight.events;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Polled by M5's live feed every few seconds — see {@link RecentEventsFeed} for what's kept. */
@RestController
@RequestMapping("/api/events")
public class EventsController {

  private final RecentEventsFeed feed;

  public EventsController(RecentEventsFeed feed) {
    this.feed = feed;
  }

  @GetMapping
  public List<RecentEvent> since(@RequestParam(defaultValue = "0") long since) {
    return feed.since(since);
  }
}
