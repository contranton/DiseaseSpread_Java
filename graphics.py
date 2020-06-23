from matplotlib import pyplot as plt
import numpy as np

if __name__ == '__main__':
    print("Starting plot")
    data = []
    with open("data.csv", "r") as f:
        for line in f.readlines():            
            data.append(np.array([float(s) for s in line.strip()[:-1].split(";")]))

    data = np.array(data)

    # N_Intervals , # N_Agents
    [plt.plot(data[:,0], data[:, i], linewidth=0.3) for i in range(data.shape[1])[1:100:]]

    plt.savefig("out.pdf")
    print("Finished plot")