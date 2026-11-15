# SGPlayerParams (groupAH)

This README explains what `SGPlayerParams.java` is and how you can build and run a small example that exercises it.

Location
- `src/main/java/groupAH/SGPlayerParams.java`
- Related player: `src/main/java/groupAH/SGPlayer.java`

## Run as tournament agent (JSON config)

To use `SGPlayer` in a tournament run, create a JSON config file that describes the agent parameters. Place the file in the player directory referenced by your tournament config (for example `json/assignment1/tournamentPlayers`).

Example file (save as `json/assignment1/tournamentPlayers/Alice.json`):

```json
{
  "class": "groupAH.SGPlayerParams",
  "rolloutLength": 100,
  "maxTreeDepth": 100,
  "discountFactor": 1.0,
  "heuristic": {
    "class": "groupAH.SGHeuristic"
  },
  "budgetType": "BUDGET_TIME",
  "budget": 1000,
  "actionPolicy": "SIMPLE",
  "progressiveBiasWeight": 1.0
}
```

Notes:
- The `class` field must match the parameter class: `groupAH.SGPlayerParams`.
- The `heuristic` field uses the framework's JSON format to create a `ScoreHeuristic` instance.
- You can create one JSON file per agent you want in the player directory. Filenames are used when enumerating players.

Running a tournament that picks up this player directory
