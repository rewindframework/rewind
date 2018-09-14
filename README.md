# Rewind

> When replaying your tests isn't enough.

Rewind framework was created to aide end-to-end testing especially when your tests needs to handle the coordination of multiple resources.
It isn't a real testing framework.
Instead, it's a set of clear defined concept where each concept's implementation can use whatever technology it wants.
It uses whatever testing framework you are most comfortable with.
Obviously, there are a set of technologies that work better together, and are a natural choice.

## Development

### Where are those concepts? I only see an opinionated implementation.
You are right.
As much as I would love to dedicate my time at developing this framework further, it is been improved on a as needed basis.
The concept are clear, they just need to be correctly layed out in an unopinionated way.
The framework has already been proven internally at an previous employment.
The implementation used Salt, Py.Test and Gradle as the major technologies.
For my current need, those technologies, with the exception of Gradle, are not the right fit, so the reimplementation is been done with a different sets of technologies.
