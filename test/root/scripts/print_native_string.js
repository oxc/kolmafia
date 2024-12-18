const {print} = require("kolmafia");
try {
  print(new String("That's ridiculous. It's not even funny."));
} catch (e) {
  print("exception: " + e.message);
}
