from matplotlib import pyplot as plt
from matplotlib.offsetbox import AnchoredText
from matplotlib.backends.backend_pdf import PdfPages
import numpy as np
import os

plt.rc("mathtext", fontset="stixsans")

# https://stackoverflow.com/a/10482477


def align_yaxis(ax1, v1, ax2, v2):
    """adjust ax2 ylimit so that v2 in ax2 is aligned to v1 in ax1"""
    _, y1 = ax1.transData.transform((0, v1))
    _, y2 = ax2.transData.transform((0, v2))
    inv = ax2.transData.inverted()
    _, dy = inv.transform((0, 0)) - inv.transform((0, y1-y2))
    miny, maxy = ax2.get_ylim()
    ax2.set_ylim(miny+dy, maxy+dy)


def plot(m_id, job):
    print(f"Plot {job}...", end="")
    data = []
    with open(f"data_{m_id}/{job}.csv", "r") as f:
        N = int(f.readline())
        for line in f.readlines():
            data.append(np.array([float(s)
                        for s in line.strip()[:-1].split(";")]))

    # Extract time and N
    data = np.array(data)
    t = data[:, 0].copy()

    # Number of sick people
    n_sick = data[:, 1]

    # Data now holds only the trayectories
    data = data[:, 2:]

    #############
    # Statistics
    # NaN by default to write to file easily
    stats = {"N_{max}": {"val": np.nan, "str": "nan"},
             "p_{max}": {"val": np.nan, "str": "nan"},
             "t_{crit}": {"val": np.nan, "str": "nan"},
             "t_{rise}": {"val": np.nan, "str": "nan"},
             "t_{heal}": {"val": np.nan, "str": "nan"},
             "t_{eradicate}": {"val": np.nan, "str": "nan"}}

    max_N = int(n_sick.max())   # Max infected
    stats["N_{max}"] = {"val": max_N, "str": f"{max_N:4}"}
    stats["p_{max}"] = {"val": max_N/N, "str": f"{max_N/N*100:2.1f}\%"}

    # Critical and rise time (only if 100% infection)
    if(max_N == N):
        crit_time = t[np.where(n_sick <= N/np.e)[0][-1]]
        sett_time = t[np.where(n_sick <= N*(1-1/np.e))[0][-1]]
        rise_time = sett_time - crit_time
        stats["t_{crit}"] = {"val": crit_time, "str": f"{crit_time:3.2f}"}
        stats["t_{rise}"] = {"val": rise_time, "str": f"{rise_time:3.2f}"}
    # Time to full heal (If eradicates)
    elif(n_sick[-1] == 0):
        # Considering sick cases (health > 50)
        t_sick = np.where(n_sick == 1)[0]
        if np.any(t_sick):
            start_time = t[t_sick[0]]
            heal_time = t[t_sick[-1]] - start_time
            stats["t_{heal}"] = {"val": heal_time, "str": f"{heal_time:3.2f}"}

        # Considering
        erad_time = t[np.where(n_sick == 0)[0][-1]]
        stats["t_{eradicate}"] = {"val": erad_time, "str": f"{erad_time:3.2f}"}

    ###########
    # Plotting
    fig, ax = plt.subplots()

    # Plot individual trajectories
    ax.set_xlabel("Tiempo")
    ax.set_ylabel("Salud")
    ax.set_ylim((0, 100))
    ax.grid(alpha=0.3)
    ax.tick_params(axis='y')
    [ax.plot(t, data[:, i], linewidth=0.3, alpha=0.8)
             for i in range(data.shape[1])]

    # Plot number of sick
    ax2 = ax.twinx()
    ax2.set_ylabel("# Enfermos", color='red')
    ax2.set_ylim((0, N))
    ax2.set_yticks(np.arange(0, N+1, N//5))
    ax2.grid(alpha=0.3)
    ax2.plot(t, n_sick, color='red')
    ax2.tick_params(axis='y', labelcolor='red')

    align_yaxis(ax, 0, ax2, 0)

    # Show statistics
    textstr = "\n".join(
        ["$"+key+f" = {val['str']}$" for key, val in stats.items()])
    at = AnchoredText(textstr, loc=(
        "lower right" if max_N == N else "upper right"))
    at.patch.set_boxstyle("round, pad=0., rounding_size=0.2")
    ax.add_artist(at)

    fig.tight_layout()
    print(f"done")

    ###########################
    # Write statistics to file
    with open(f"results/{m_id}.csv", 'a') as f:
        f.write(",".join([str(i['val']) for i in stats.values()]))
        f.write("\n")

    return fig

def read_data_names():
    for root, dirs, files in os.walk("."):
        if "data" in root:
            m_id=root.split("_")[-1].split(".")[0]
            names=[]
            for full_name in files:
                name, ext=full_name.split(".")
                if ext == "csv":
                    names.append(name)
            yield m_id, names

if __name__ == "__main__":
    for m_id, jobs in read_data_names():
        with open(f"results/{m_id}.csv", 'w') as f:
            f.write("N_{max}, p_{max}, t_{crit}, t_{rise}, t_{heal}, t_{eradicate}\n")

        pp=PdfPages(f"plots/{m_id}.pdf")
        for job in jobs:
            fig=plot(m_id, job)
            fig.savefig(pp, format="pdf")
            plt.close()
        pp.close()
