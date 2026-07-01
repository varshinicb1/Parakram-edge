from setuptools import setup, find_packages

setup(
    name="parakram-bridge",
    version="1.0.0",
    description="AgentOS Bridge SDK — let AI agents discover and control phone hardware",
    packages=find_packages(),
    install_requires=[
        "httpx>=0.27.0",
        "websockets>=12.0",
        "zeroconf>=0.132.0",
    ],
    python_requires=">=3.10",
)
