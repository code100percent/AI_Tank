package com.example.new_tank_ai.model

enum class RobotExpression(
    val title: String,
    val defaultMessage: String
) {
    NEUTRAL("Neutral", "Ready when you are."),
    HAPPY("Happy", "Looking good!"),
    EXCITED("Excited", "Let's go!"),
    LISTENING("Listening", "I'm listening."),
    THINKING("Thinking", "Let me think..."),
    SPEAKING("Speaking", "Here's what I think."),
    SURPRISED("Surprised", "Whoa!"),
    CONFUSED("Confused", "I didn't get that."),
    SLEEPY("Sleepy", "Time for a little rest."),
    SAD("Sad", "Something seems wrong."),
    ANGRY("Angry", "That wasn't very nice."),
    WINKING("Winking", "You got this!")
}
