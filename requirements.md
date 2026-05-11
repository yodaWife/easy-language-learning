# Language learning app
We are building an app that is supposed to help with any language learning

## General overview
The app is a spring boot up with custom designed UI.
When user opens the webpage he can choose from multiple ways of learning, for now only two are available "flashcards" and "match".

### Input
The input to the app is database of words, for now in a form of CSV file of a format:

```
ENGLISH;HUNGARIAN;EXAMPLE
```
The expected columns are 
1. Original Language
2. Foreign Language
3. Example of the usage of the word

This file is later called "language database" or "language file".
For the simplicity in further documentation the original language will be called "FROM" and the foreing language will be called "TO".
Take into consideration that eventually we may want to replace the language file with a database.

### Match
When 'match' is selected the user get's redirected to a page where in two columns 3 rows of words are displayed, on the left we have the "FROM" column where the word in user known language is displayed and the "TO" column where the foreign equivalents of the words are displayed.
User's job is to drag and drop words that match "FROM"-"TO". 
Replace the "FROM" and "TO" with names of the language presented in the header of the language file.
The words can be matched in both directions. 
If the words are matched correctly they flash once green and dissapear, if the matching is incorrect the words are getting back to their original positions and they once flash red.
Matching game includes scoring that is described later in the doc.
One session of the match includes 30 words to match, user can see same words multiple times and the words and their ordering are always selected randomly.

### Flashcards
In this scenario a single word is displayed in the screen in the "FROM" language. When user clicks on the word the card with word flips and on the other side user can see the "TO" equivalent of the word and it's example.
This is a practice mode and no scoring system is included.

## Scoring
There are two scoring systems within the app, per game and per user.

**Per game scoring**
When user selects mode that supports scoring his performance is tracked within the game.
There is a clear information displayed on the UI about how many challanges were solved succesfully and how many were not.
At the end of the match the summary of the performance is displayed with percentage of success (success+failures/sum of tries) with appropriate comment:
- 100% score -> You did it!
- 85-99% score -> Almost!
- anything below 85% -> Let's practice some more!

**Per user scoring**
The app supports generic scoring system (in the future it should be reusable by other methods of learning).
To enable scoring system the user needs to specify it's name before selecting the learning mode, this can happen by selecting one of the existing names or supplying a new one.
For every word last 10 tries are tracked by the app where all successes and failures are counted, for now this information is not used for anything else than tracking purposes but the plan is to prepare a formula that prefers selecting words for practice that user made more mistakes with.
Every mistake has to be counted when it occurs, example: if user matches the words incorrectly twice during one matching session then both tries should be registered.

It is possible to play witout specifying user name, in this case only per game scoring is enabled, the user scoring is not available and the results are not tracked.

### Scoring persistence
For now scores are persisted in the form of csv file of a strucutre:
```
USER;FROM;TO;HISTORY
```
Where FROM and TO is a pair of both original and foreign language words and HISTORY is represented as a FIFO array with max 10 elements.
We track both original and foreign language because it is possible to support more than one language and therfore only the pair creates a complete information.

The csv file with history has to be loaded at the startup of the service to asure user can select himself at the welcome page and can continue practice.

## Traps
- Different languages use different characters, right font needs to be use in the UI so it can support as much of alphanumeric characters as possible
- If a word is not present in the scoring history treat it as new and create a new entry after user's first scored attempt with it.
