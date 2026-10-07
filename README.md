# ASField Airsoft Matchmaker

ASField is an Android application for managing an airsoft player roster and recommending balanced matches from 1v1 through 4v4.

It is a rule-based recommendation system:

```text
Player and group inputs → matchmaking rules → recommended teams
```

## Main features

- Add, rename, activate/deactivate, and remove players.
- Create groups and pairs with relationship rules.
- Generate 1v1, 2v2, 3v3, or 4v4 matches.
- Prefer players with fewer rounds in their current session.
- Manually create a blank match and fill individual team slots.
- Adjust an existing match without duplicating a player.
- View sortable statistics for the current session, today, this week, or all time.
- Keep match history separate from the match-generation screen.

## Matchmaking rules

### Active players

Only players whose `Present?` switch is on can be selected for an automatically generated or manually adjusted match.

If a match is re-rolled, players already in the current match are avoided when enough other active players are available. If there are not enough replacements, some existing players may be reused.

### Fairness

Match selection prioritizes fairness in this order: the candidate's total session appearances, its highest individual session count, the spread between its highest and lowest count, and count variance. This makes unused players win over pairs that have already played, while still preventing one frequently selected player from hiding behind several players with low counts. Exact ties are randomized, so equally fair lineups do not always repeat in the same order.

The matcher also consults the ten most recent completed matches and penalizes repeating the same lineup. This is a soft preference: fairness and valid group rules still take priority, and repetition remains possible when there are too few alternatives.

When a player switches `Present?` off, their session counters are reset. Switching them on starts a new active session with zero session rounds.

Daily, weekly, and all-time counters are retained separately for reporting.

### Group rules

- **Same Team**: members must be placed on the same team when the group fits on one team.
- **Same Match**: members must be selected into the same match when the group fits inside the selected match size.
- **Opposing Teams**: selected members must be split across the two teams.

Some rules are adapted to the selected match size:

- In 1v1, a two-person Same Team group is treated as Same Match because two players cannot occupy the same one-person team.
- In 2v2, a three-person Same Team group is treated as Same Match because three players cannot occupy one two-person team.
- If a group is larger than the entire match, it may be split across matches instead of causing an error.

Overlapping groups are allowed. For example, if A/B/C are Same Match and A/D are Opposing Teams, the matcher keeps A/B/C in one match and places A and D on opposite teams when possible.

If constraints conflict, the app randomly relaxes one group rule at a time until a valid match exists, then reports which rule(s) were relaxed. This preserves as many rules as possible instead of dropping every rule immediately. If no valid constrained match can be made, a final relaxed fallback prevents a crash.

## Manual match editing

Choose **Choose Players Manually** to create a blank match. Then choose **Adjust Selected Players** to edit each Team Alpha and Team Bravo slot.

- Each slot can be cleared.
- Players already occupying another slot are hidden from that slot's selector.
- Every slot must contain a different active player before saving.
- An active match must be explicitly cancelled before selecting a different match size.

## Statistics

The Stats tab presents one period at a time in a sortable table:

- Current session
- Today
- This week
- All time

The History tab contains match history, an activity log, and the daily/weekly reset controls. The activity log records players joining/leaving and statistics resets. Opposite presence changes during the same period between completed games cancel each other out, so a quick leave/rejoin does not create noise. Resetting Today also resets the current session. Resetting This Week resets the week, today, and current session. Neither reset deletes players, groups, all-time statistics, or match history.

## Building the project

Requirements:

- Android Studio with a compatible Android SDK and JDK.
- Android SDK 37.

Open the project in Android Studio and run the `app` configuration. The application ID is:

```text
com.wazuhonde.asfield
```

## Suggested demonstration flow

For a project presentation video:

1. Add players and mark who is present.
2. Create a Same Team or Same Match group.
3. Select a match size and generate a recommendation.
4. Show the resulting teams and explain the fairness/session rule.
5. Demonstrate Adjust Selected Players.
6. Show the Stats and History tabs.

